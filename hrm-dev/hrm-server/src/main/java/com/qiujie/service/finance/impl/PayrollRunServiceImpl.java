package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.PageResult;
import com.qiujie.config.PayrollScheduleProperties;
import com.qiujie.dto.finance.PayrollRunQuery;
import com.qiujie.dto.finance.PayrollRunTriggerRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollRun;
import com.qiujie.entity.Station;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.PayrollMapper;
import com.qiujie.mapper.PayrollRunMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.PayrollRunService;
import com.qiujie.service.finance.port.PayrollRunNotifier;
import com.qiujie.service.finance.support.PayrollBillType;
import com.qiujie.service.finance.support.PayrollRunFailureSupport;
import com.qiujie.service.finance.support.PayrollRunSkipCode;
import com.qiujie.service.finance.support.PayrollRunStatus;
import com.qiujie.service.finance.support.PayrollRunTriggerType;
import com.qiujie.service.finance.support.PayrollSchedulePlanner;
import com.qiujie.service.finance.support.PayrollStatus;
import com.qiujie.util.UserContext;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 自动算薪运行服务实现（I-4 / I-5 + 调度执行）。
 * <p>
 * <b>编排层不加事务</b>：claim（Tx1）/ 生成（Tx2）/ 终态（Tx3）由 {@link PayrollRunTxHandler} 三段独立提交；
 * 单驿站异常在 {@link #executeIfDue} 内被捕获并归 FLAGGED，不外溢（逐驿站隔离）。
 * <p>
 * <b>SKIPPED 归类（算法 R9）</b>：配置非法 → {@code CONFIG_INVALID}、命中 9405 → {@code BLOCKED_9405}、
 * 存在人工 DRAFT → {@code DRAFT_PROTECTED}；「未启用 / 未到点 / 当日已尝试 / 已占位」<b>不产生运行记录</b>。
 * <p>
 * <b>9405 按驿站收敛</b>：自动路径以 {@code stationId} 收敛生成入参，{@code generate} 内部据此收敛判定，
 * 复用与手工路径同一收敛口径（C-7），不另写一套。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollRunServiceImpl implements PayrollRunService {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    private final PayrollRunMapper payrollRunMapper;
    private final StationPayrollSettingMapper settingMapper;
    private final StationMapper stationMapper;
    private final PayrollMapper payrollMapper;
    private final EmployeeMapper employeeMapper;
    private final PayrollRunTxHandler txHandler;
    private final PayrollScheduleProperties scheduleProperties;
    private final PayrollRunNotifier notifier;

    // ==================== I-4 手工触发 ====================

    @Override
    public PayrollRunVO trigger(PayrollRunTriggerRequest request) {
        Long stationId = request == null ? null : request.getStationId();
        String month = request == null ? null : request.getMonth();
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "stationId 必填");
        }
        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        if (month == null || !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        StationPayrollSetting setting = findSetting(stationId);
        if (setting == null || !Integer.valueOf(1).equals(setting.getEnabled())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_RUN_DISABLED);
        }

        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        ZonedDateTime now = now();
        LocalDate today = now.toLocalDate();
        LocalTime payrollTime = PayrollSchedulePlanner.parseTimeOrNull(setting.getPayrollTime());
        LocalDateTime dueAt = dueAt(setting, payrollTime, ym);

        // 占位（SUCCESS/SKIPPED/RUNNING）→ 9410；当日已尝试 → 9410（硬防线为 uk_claim / uk_attempt）
        if (existsOccupied(stationId, month) || existsAttempt(stationId, month, today)) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_RUN_IN_PROGRESS);
        }
        // 日粒度闸门：须 now ≥ 当日尝试钟点（§3.3 I-4；未到点不予执行）
        if (payrollTime != null) {
            LocalDateTime attemptTime = PayrollSchedulePlanner.attemptTimeOf(today, dueAt, payrollTime, catchUpTime());
            if (now.toLocalDateTime().isBefore(attemptTime)) {
                throw new BusinessException(ErrorCode.FINANCE_PAYROLL_RUN_IN_PROGRESS,
                        "该驿站该账期尚未到当日可执行时刻（" + attemptTime.toLocalTime() + "）");
            }
        }
        return runPipeline(setting, month, dueAt, PayrollRunTriggerType.MANUAL.name(),
                UserContext.getUserId(), now, true);
    }

    // ==================== 调度执行（executeIfDue） ====================

    @Override
    public PayrollRunVO executeIfDue(StationPayrollSetting setting, ZonedDateTime now) {
        if (setting == null || setting.getStationId() == null) {
            return null;
        }
        String month = YearMonth.from(now).toString();
        YearMonth ym = YearMonth.from(now);
        LocalDate today = now.toLocalDate();
        LocalTime payrollTime = PayrollSchedulePlanner.parseTimeOrNull(setting.getPayrollTime());
        LocalDateTime dueAt = dueAt(setting, payrollTime, ym);

        // 短路①：未到算薪日
        if (today.isBefore(dueAt.toLocalDate())) {
            return null;
        }
        // 短路②：当日尝试钟点未到（复-3：统一 catch-up-time-of-day）
        if (payrollTime != null) {
            LocalDateTime attemptTime = PayrollSchedulePlanner.attemptTimeOf(today, dueAt, payrollTime, catchUpTime());
            if (now.toLocalDateTime().isBefore(attemptTime)) {
                return null;
            }
        }
        // 短路③④：已成功 / 已占位（claim 短路，不产生运行记录）
        Long stationId = setting.getStationId();
        if (existsOccupied(stationId, month)) {
            return null;
        }
        // 短路⑤：当日已尝试（应用层优化；硬防线 uk_attempt）
        if (existsAttempt(stationId, month, today)) {
            return null;
        }
        String triggerType = PayrollSchedulePlanner.triggerType(today, dueAt);
        return runPipeline(setting, month, dueAt, triggerType, null, now, false);
    }

    // ==================== I-5 分页 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<PayrollRunVO> list(PayrollRunQuery query) {
        PayrollRunQuery safe = query == null ? new PayrollRunQuery() : query;
        // id 精确查询：命中返回单条；未命中 → 9409（运行记录不存在）
        if (safe.getId() != null) {
            PayrollRun run = payrollRunMapper.selectById(safe.getId());
            if (run == null) {
                throw new BusinessException(ErrorCode.FINANCE_PAYROLL_RUN_NOT_EXISTS);
            }
            return PageResult.of(1, safe.getPageNum(), safe.getPageSize(), List.of(toRunVO(run, operatorName(run.getOperatorId()))));
        }
        if (safe.getMonth() != null && !safe.getMonth().isBlank() && !MONTH_PATTERN.matcher(safe.getMonth()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        if (safe.getStatus() != null && !safe.getStatus().isBlank() && !PayrollRunStatus.isValid(safe.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }
        if (safe.getTriggerType() != null && !safe.getTriggerType().isBlank()
                && !PayrollRunTriggerType.isValid(safe.getTriggerType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "triggerType 取值非法");
        }
        LambdaQueryWrapper<PayrollRun> wrapper = new LambdaQueryWrapper<PayrollRun>()
                .eq(safe.getStationId() != null, PayrollRun::getStationId, safe.getStationId())
                .eq(safe.getMonth() != null && !safe.getMonth().isBlank(), PayrollRun::getTargetMonth, safe.getMonth())
                .eq(safe.getStatus() != null && !safe.getStatus().isBlank(), PayrollRun::getStatus, safe.getStatus())
                .eq(safe.getTriggerType() != null && !safe.getTriggerType().isBlank(),
                        PayrollRun::getTriggerType, safe.getTriggerType())
                .orderByDesc(PayrollRun::getStartTime)
                .orderByDesc(PayrollRun::getId);
        Page<PayrollRun> page = payrollRunMapper.selectPage(
                new Page<>(safe.getPageNum(), safe.getPageSize()), wrapper);

        Set<Long> operatorIds = new LinkedHashSet<>();
        for (PayrollRun run : page.getRecords()) {
            if (run.getOperatorId() != null) {
                operatorIds.add(run.getOperatorId());
            }
        }
        Map<Long, String> nameById = operatorNames(operatorIds);
        List<PayrollRunVO> list = new ArrayList<>(page.getRecords().size());
        for (PayrollRun run : page.getRecords()) {
            list.add(toRunVO(run, nameById.get(run.getOperatorId())));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    // ==================== 僵死回收 ====================

    @Override
    public int reclaimStale(ZonedDateTime now) {
        if (!scheduleProperties.isStaleReclaimEnabled()) {
            return 0;
        }
        int timeout = Math.max(1, scheduleProperties.getRunningTimeoutMinutes());
        LocalDateTime threshold = now.toLocalDateTime().minusMinutes(timeout);
        List<PayrollRun> reclaimed = txHandler.reclaimStale(threshold, now.toLocalDateTime());
        if (!reclaimed.isEmpty()) {
            // 回收命中必须触发失败告警钩子（消除「静默失效」；B4a 投递时按 notify-on-fail 管控）
            notifier.onStaleReclaimed(reclaimed);
            log.warn("僵死 RUNNING 回收命中 {} 条（threshold={}）", reclaimed.size(), threshold);
        }
        return reclaimed.size();
    }

    // ==================== 私有：执行流水线 ====================

    /**
     * 认领 → 配置校验 → DRAFT 保护 → 生成 → 终态。调用方已完成「未到点 / 已占位 / 当日已尝试」短路。
     *
     * @param manual 手工路径：认领冲突抛 {@code 9410}；自动路径：认领冲突返回 {@code null}（静默短路）
     */
    private PayrollRunVO runPipeline(StationPayrollSetting setting, String month,
                                     LocalDateTime dueAt, String triggerType, Long operatorId,
                                     ZonedDateTime now, boolean manual) {
        Long stationId = setting.getStationId();
        LocalDateTime startTime = now.toLocalDateTime();
        PayrollRun run = new PayrollRun();
        run.setStationId(stationId);
        run.setTargetMonth(month);
        run.setAttemptDate(now.toLocalDate());
        run.setTriggerType(triggerType);
        run.setDueAt(dueAt);
        run.setStatus(PayrollRunStatus.RUNNING.name());
        run.setClaimKey(month);
        run.setOperatorId(operatorId);
        run.setStartTime(startTime);

        // Tx1：认领槽位（独立短事务先提交；撞唯一键即被 InnoDB 原子拒绝）
        try {
            txHandler.claim(run);
        } catch (DuplicateKeyException e) {
            if (manual) {
                throw new BusinessException(ErrorCode.FINANCE_PAYROLL_RUN_IN_PROGRESS);
            }
            log.info("认领槽位被占用，自动路径短路：stationId={}, month={}", stationId, month);
            return null;
        }

        LocalDateTime finishTime = now.toLocalDateTime();
        // 配置校验（终止类跳过，不重试）
        if (!PayrollSchedulePlanner.isConfigValid(setting.getPayrollDay(), setting.getPayrollTime())) {
            String reason = "该驿站算薪配置非法（算薪日须 1-31、时间须 HH:mm）";
            txHandler.finish(run.getId(), PayrollRunStatus.SKIPPED.name(), month,
                    PayrollRunSkipCode.CONFIG_INVALID, reason, null, null, finishTime);
            return applyResult(run, PayrollRunStatus.SKIPPED, PayrollRunSkipCode.CONFIG_INVALID, reason, null, null);
        }
        // DRAFT 保护（算法 §1.5.2 (a)）：自动路径不覆盖重建人工草稿，避免与手工录入并发丢数据
        if (hasDraft(stationId, month)) {
            String reason = "该驿站该账期存在人工草稿单，自动路径保护性跳过（待人工处理后重跑）";
            txHandler.finish(run.getId(), PayrollRunStatus.SKIPPED.name(), month,
                    PayrollRunSkipCode.DRAFT_PROTECTED, reason, null, null, finishTime);
            return applyResult(run, PayrollRunStatus.SKIPPED, PayrollRunSkipCode.DRAFT_PROTECTED, reason, null, null);
        }

        try {
            // Tx2：业务事务（生成；stationId 收敛 → 复用既有 9405 收敛口径，不新建第二套算薪逻辑）
            PayrollGenerateVO generated = txHandler.executeGenerate(stationId, month);
            // Q6：生成即逐单自动提交待审（失败逐单隔离，不回滚已生成/已提交的单）
            AutoSubmitOutcome autoSubmit = autoSubmit(generated);
            // Tx3：终态回写（成功占位）
            txHandler.finish(run.getId(), PayrollRunStatus.SUCCESS.name(), month, null, null,
                    generated.getCreated(), null, finishTime);
            // 先回填内存终态，再于事务提交后通知（B4a 投递时 run 已是终态）
            PayrollRunVO vo = applyResult(run, PayrollRunStatus.SUCCESS, null, null, generated.getCreated(), null);
            vo.setSubmittedCount(autoSubmit.submitted());
            vo.setSkippedCount(autoSubmit.skipped());
            // 类型 7 仅投递确已落 PENDING_APPROVAL 的单据（提交失败仍为 DRAFT 者不投）
            notifier.onSucceeded(run, autoSubmit.submittedIds(), autoSubmit.submitted());
            return vo;
        } catch (BusinessException be) {
            if (be.getCode() == ErrorCode.FINANCE_PAYROLL_GENERATED.getCode()) {
                // 命中 9405 → SKIPPED（业务终止，不重试）
                txHandler.finish(run.getId(), PayrollRunStatus.SKIPPED.name(), month,
                        PayrollRunSkipCode.BLOCKED_9405, be.getMessage(), null, null, finishTime);
                return applyResult(run, PayrollRunStatus.SKIPPED, PayrollRunSkipCode.BLOCKED_9405, be.getMessage(), null, null);
            }
            return finishFailed(run, be, finishTime);
        } catch (Exception e) {
            return finishFailed(run, e, finishTime);
        }
    }

    /** 失败终态：释放跨日占位（claim_key=NULL）、写脱敏原因、触发失败钩子；异常不外抛（由 run 记录承载归因） */
    private PayrollRunVO finishFailed(PayrollRun run, Exception e, LocalDateTime finishTime) {
        String failReason = PayrollRunFailureSupport.describe(e);
        txHandler.finish(run.getId(), PayrollRunStatus.FAILED.name(), null, null, null, null, failReason, finishTime);
        PayrollRunVO vo = applyResult(run, PayrollRunStatus.FAILED, null, null, null, failReason);
        notifier.onFailed(run);
        log.error("自动算薪执行失败：stationId={}, month={}", run.getStationId(), run.getTargetMonth(), e);
        return vo;
    }

    /**
     * 自动算薪成功分支的自动提交（Q6）：对本次生成单据逐单 {@code submit}，复用既有
     * {@link com.qiujie.service.finance.PayrollService#submit(java.util.List)} 落 {@code PENDING_APPROVAL}。
     * <p><b>为什么逐单独立事务</b>：{@code submit} 是「全量校验后统一更新」的强一致批量口，整批调用时任一单失败会连累整批；
     * 经 {@link PayrollRunTxHandler#submitOne(Long)}（{@code REQUIRES_NEW}）逐单各自成事务，失败只影响本单，
     * <b>不回滚本次已成功的生成</b>（生成已在 Tx2 提交）。
     * <p><b>失败口径</b>：失败单一律保持 {@code DRAFT}，写 {@code payroll_log(AUTO_SUBMIT_SKIPPED)} 留痕并计入
     * {@code skippedCount}，由管理员后续手工 {@code submit}；留痕写入失败仅告警，不阻断主流程。
     */
    private AutoSubmitOutcome autoSubmit(PayrollGenerateVO generated) {
        List<Long> ids = generated == null ? null : generated.getPayrollIds();
        if (ids == null || ids.isEmpty()) {
            return new AutoSubmitOutcome(0, 0, List.of());
        }
        List<Long> submitted = new ArrayList<>(ids.size());
        int skipped = 0;
        for (Long payrollId : ids) {
            if (payrollId == null) {
                continue;
            }
            try {
                txHandler.submitOne(payrollId);
                submitted.add(payrollId);
            } catch (Exception e) {
                skipped++;
                String reason = PayrollRunFailureSupport.describe(e);
                log.warn("自动算薪提交审核未成功（该单保持 DRAFT）：payrollId={}，原因={}", payrollId, reason);
                try {
                    txHandler.recordSubmitSkipped(payrollId, reason);
                } catch (Exception traceError) {
                    // 留痕失败不得阻断主流程：单据仍为 DRAFT，可被管理员手工提交
                    log.warn("自动提交跳过留痕写入失败：payrollId={}", payrollId, traceError);
                }
            }
        }
        return new AutoSubmitOutcome(submitted.size(), skipped, List.copyOf(submitted));
    }

    /** 回填内存态并转出参（供触发响应；不依赖二次查询） */
    private PayrollRunVO applyResult(PayrollRun run, PayrollRunStatus status, String skipCode, String skipReason,
                                     Integer generatedCount, String failReason) {
        run.setStatus(status.name());
        run.setSkipCode(skipCode);
        run.setSkipReason(skipReason);
        run.setGeneratedCount(generatedCount);
        run.setFailReason(failReason);
        return toRunVO(run, operatorName(run.getOperatorId()));
    }

    // ==================== 私有：查询 / 转换 ====================

    private LocalDateTime dueAt(StationPayrollSetting setting, LocalTime payrollTime, YearMonth ym) {
        int day = setting.getPayrollDay() == null ? 1 : setting.getPayrollDay();
        if (payrollTime == null) {
            // 配置非法（时间无法解析）：给可落库的兜底 dueAt（当月首日 00:00），后续按 CONFIG_INVALID 跳过
            return LocalDateTime.of(ym.getYear(), ym.getMonthValue(), Math.min(Math.max(day, 1), ym.lengthOfMonth()), 0, 0);
        }
        return PayrollSchedulePlanner.computeDueAt(day, payrollTime, ym);
    }

    private LocalTime catchUpTime() {
        return PayrollSchedulePlanner.parseTimeOrNull(scheduleProperties.getCatchUpTimeOfDay());
    }

    /** 跨日终态占位判定：存在 claim_key=month 的行（RUNNING/SUCCESS/SKIPPED） */
    private boolean existsOccupied(Long stationId, String month) {
        Long count = payrollRunMapper.selectCount(new LambdaQueryWrapper<PayrollRun>()
                .eq(PayrollRun::getStationId, stationId)
                .eq(PayrollRun::getClaimKey, month));
        return count != null && count > 0;
    }

    /** 日粒度闸门判定：同驿站同账期同自然日是否已有尝试行 */
    private boolean existsAttempt(Long stationId, String month, LocalDate day) {
        Long count = payrollRunMapper.selectCount(new LambdaQueryWrapper<PayrollRun>()
                .eq(PayrollRun::getStationId, stationId)
                .eq(PayrollRun::getTargetMonth, month)
                .eq(PayrollRun::getAttemptDate, day));
        return count != null && count > 0;
    }

    /** DRAFT 保护判定：该驿站该账期是否存在人工草稿月度单 */
    private boolean hasDraft(Long stationId, String month) {
        Long count = payrollMapper.selectCount(new LambdaQueryWrapper<Payroll>()
                .eq(Payroll::getStationId, stationId)
                .eq(Payroll::getMonth, month)
                .eq(Payroll::getBillType, PayrollBillType.MONTHLY.name())
                .eq(Payroll::getStatus, PayrollStatus.DRAFT.name()));
        return count != null && count > 0;
    }

    private StationPayrollSetting findSetting(Long stationId) {
        List<StationPayrollSetting> rows = settingMapper.selectList(new LambdaQueryWrapper<StationPayrollSetting>()
                .eq(StationPayrollSetting::getStationId, stationId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String operatorName(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(operatorId);
        return employee == null ? null : employee.getRealName();
    }

    private Map<Long, String> operatorNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (Employee employee : employeeMapper.selectBatchIds(ids)) {
            map.put(employee.getId(), employee.getRealName());
        }
        return map;
    }

    private PayrollRunVO toRunVO(PayrollRun run, String operatorName) {
        PayrollRunVO vo = new PayrollRunVO();
        vo.setId(run.getId());
        vo.setStationId(run.getStationId());
        vo.setTargetMonth(run.getTargetMonth());
        vo.setAttemptDate(run.getAttemptDate());
        vo.setTriggerType(run.getTriggerType());
        vo.setDueAt(run.getDueAt());
        vo.setStatus(run.getStatus());
        vo.setSkipCode(run.getSkipCode());
        vo.setSkipReason(run.getSkipReason());
        vo.setGeneratedCount(run.getGeneratedCount());
        vo.setFailReason(run.getFailReason());
        vo.setOperatorName(operatorName);
        vo.setStartTime(run.getStartTime());
        vo.setFinishTime(run.getFinishTime());
        return vo;
    }

    /** 判定时区（配置非法时回落 Asia/Shanghai 并告警，避免每个 tick 抛异常） */
    private ZoneId zone() {
        try {
            return ZoneId.of(scheduleProperties.getZone());
        } catch (Exception e) {
            log.warn("hrm.payroll.schedule.zone 非法，回落 Asia/Shanghai：{}", scheduleProperties.getZone());
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(zone());
    }

    /**
     * 自动提交结果（仅本次运行内存态）。
     * <p>{@code skipped} 未持久化到 {@code payroll_run}（无对应列，加列属结构变更，超出本批范围）；
     * 仅随 I-4 触发响应返回运行结果，失败单另由 {@code payroll_log(AUTO_SUBMIT_SKIPPED)} 留痕可查。
     */
    private record AutoSubmitOutcome(int submitted, int skipped, List<Long> submittedIds) {
    }
}
