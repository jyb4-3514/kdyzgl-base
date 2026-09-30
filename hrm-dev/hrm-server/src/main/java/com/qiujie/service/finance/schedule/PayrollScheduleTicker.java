package com.qiujie.service.finance.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.PayrollScheduleProperties;
import com.qiujie.entity.Station;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.PayrollRunService;
import com.qiujie.service.finance.support.PayrollSchedulePlanner;
import com.qiujie.vo.finance.PayrollRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 薪资自动算薪调度器（ADR-01：进程内 {@code @Scheduled} 固定间隔轮询）。
 * <p>
 * <b>ticker 本身不加事务</b>：每驿站一个独立事务边界（由 {@code PayrollRunTxHandler} 三段交易承载），
 * 逐驿站 try/catch，单驿站失败不影响其它（算法 §1.6）。
 * <p>
 * <b>判定链</b>（算法 §1.3）：总开关 → 僵死回收扫描 → 扫描 {@code enabled=1 ∧ station.status=1} →
 * 按 {@code (dueAt, stationId)} 稳定全序 → 逐驿站 {@code executeIfDue}（未到算薪日 / 当日钟点未到 /
 * 已成功 / 已占位 / 当日已尝试 一律短路，不产生运行记录）。
 * <p>
 * 轮询而非逐站 cron：算薪日/时间逐驿站不同，单条 cron 无法表达；轮询天然实现「宕机恢复后 ≤1 tick 内补上」
 * （ADR-01）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollScheduleTicker {

    private final PayrollScheduleProperties scheduleProperties;
    private final StationPayrollSettingMapper settingMapper;
    private final StationMapper stationMapper;
    private final PayrollRunService payrollRunService;

    /** 固定间隔轮询（fixedDelay：上一轮结束到下一轮开始计时，单实例不重叠） */
    @Scheduled(fixedDelayString = "${hrm.payroll.schedule.tick-interval-ms:600000}")
    public void tick() {
        if (!scheduleProperties.isEnabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(zone());
        // 1) 僵死回收（先做，避免僵死占位拦住本轮补跑）
        try {
            payrollRunService.reclaimStale(now);
        } catch (Exception e) {
            log.error("僵死回收扫描异常（不影响本 tick 到点执行）", e);
        }
        // 2) 扫描启用驿站（station.status=1 在内存侧过滤，避免跨表 join）
        YearMonth month = YearMonth.from(now);
        List<StationPayrollSetting> settings = settingMapper.selectList(new LambdaQueryWrapper<StationPayrollSetting>()
                .eq(StationPayrollSetting::getEnabled, 1));
        if (settings.isEmpty()) {
            return;
        }
        List<Candidate> candidates = new ArrayList<>(settings.size());
        for (StationPayrollSetting setting : settings) {
            if (setting.getStationId() == null) {
                continue;
            }
            Station station = stationMapper.selectById(setting.getStationId());
            if (station == null || !Integer.valueOf(1).equals(station.getStatus())) {
                continue;
            }
            candidates.add(new Candidate(setting, station, dueAtForSort(setting, month)));
        }
        // 稳定全序：dueAt 升序、stationId 升序 → 结果可复现
        candidates.sort(Comparator.comparing(Candidate::dueAt)
                .thenComparing(c -> c.setting().getStationId()));

        int processed = 0;
        int max = scheduleProperties.getMaxStationsPerTick();
        for (int i = 0; i < candidates.size(); i++) {
            if (max > 0 && processed >= max) {
                log.info("tick 达单轮驿站上限 {}，剩余 {} 站顺延下轮", max, candidates.size() - i);
                break;
            }
            Candidate candidate = candidates.get(i);
            try {
                PayrollRunVO result = payrollRunService.executeIfDue(candidate.setting(), now);
                if (result != null) {
                    processed++;
                }
            } catch (Exception e) {
                // 逐驿站隔离：单驿站异常只记日志，不中断整轮（算法 §1.6）
                log.error("自动算薪驿站执行异常，已隔离并继续：stationId={}",
                        candidate.setting().getStationId(), e);
            }
            if (scheduleProperties.getInterStationDelayMs() > 0 && i < candidates.size() - 1) {
                sleepInterStation();
            }
        }
        if (processed > 0) {
            log.info("自动算薪 tick 完成：执行 {} 站（候选 {} 站）", processed, candidates.size());
        }
    }

    /** 排序用 dueAt（时间非法时以当月首日 00:00 兜底；真正执行时的兜底见 Service） */
    private LocalDateTime dueAtForSort(StationPayrollSetting setting, YearMonth month) {
        LocalTime time = PayrollSchedulePlanner.parseTimeOrNull(setting.getPayrollTime());
        int day = setting.getPayrollDay() == null ? 1 : setting.getPayrollDay();
        if (time == null) {
            return LocalDateTime.of(month.getYear(), month.getMonthValue(),
                    Math.min(Math.max(day, 1), month.lengthOfMonth()), 0, 0);
        }
        return PayrollSchedulePlanner.computeDueAt(day, time, month);
    }

    /** 驿站间串行间隔（错峰节流） */
    private void sleepInterStation() {
        try {
            Thread.sleep(scheduleProperties.getInterStationDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 判定时区（配置非法时回落 Asia/Shanghai，避免每个 tick 抛异常） */
    private ZoneId zone() {
        try {
            return ZoneId.of(scheduleProperties.getZone());
        } catch (Exception e) {
            log.warn("hrm.payroll.schedule.zone 非法，回落 Asia/Shanghai：{}", scheduleProperties.getZone());
            return ZoneId.of("Asia/Shanghai");
        }
    }

    /** 候选（setting + 归属驿站 + 排序用 dueAt） */
    private record Candidate(StationPayrollSetting setting, Station station, LocalDateTime dueAt) {
    }
}
