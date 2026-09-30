package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceRuleRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.CheckPeriod;
import com.qiujie.entity.Station;
import com.qiujie.entity.WifiEntry;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceRuleMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceRuleService;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.service.attendance.support.AttendancePeriodResolver;
import com.qiujie.service.attendance.support.AttendanceSupport;
import com.qiujie.service.attendance.support.AttendanceWifiValidator;
import com.qiujie.vo.attendance.AttendanceRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 打卡规则服务实现（Mock {@code attendanceStore} 规则段 + {@code routes/attendance.js} 规则段）。
 * <p>
 * 白名单写入（防越权改归属/时间）；新驿站首存即创建并套用与 Mock 等价的默认规则。
 * <p>
 * <b>真源统一（方案 v1.2 §5 / §7）</b>：时段（{@code checkPeriods}）与 {@code workStartTime/workEndTime}
 * 改为<b>读取时由「该驿站启用班次」派生</b>，不再由规则保存写入；{@code PUT /rule} 收到非空 {@code checkPeriods}
 * 一律拒绝（U-5，通用 400），{@code checkFrequency} 只读派生（U-4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRuleServiceImpl implements AttendanceRuleService {

    /** 电子围栏原点（示例市内虚构坐标，与 Mock {@code FENCE_ORIGIN} 一致；非真实地点） */
    private static final double FENCE_ORIGIN_LONGITUDE = 117.201;
    private static final double FENCE_ORIGIN_LATITUDE = 31.821;

    private final AttendanceRuleMapper attendanceRuleMapper;
    private final StationMapper stationMapper;
    private final AttendanceShiftService attendanceShiftService;
    /** 算法参数：班次序号界值 / 时段派生（真源统一后规则出参由班次派生） */
    private final AlgoProperties algoProperties;

    @Override
    @Transactional(readOnly = true)
    public AttendanceRuleVO getRule(Long stationId) {
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        AttendanceRule rule = findRule(stationId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED);
        }
        return toVO(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceRuleVO> listRules() {
        LambdaQueryWrapper<AttendanceRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AttendanceRule::getId);
        List<AttendanceRule> rules = attendanceRuleMapper.selectList(wrapper);
        // 批量预取各驿站启用班次后内存派生，规避逐条查班次的 N+1（方案 §10.4 RK-1）
        Set<Long> stationIds = new LinkedHashSet<>();
        for (AttendanceRule rule : rules) {
            if (rule.getStationId() != null) {
                stationIds.add(rule.getStationId());
            }
        }
        Map<Long, List<AttendanceShift>> shiftsByStation = attendanceShiftService.enabledShiftsByStation(stationIds);
        List<AttendanceRuleVO> list = new ArrayList<>(rules.size());
        for (AttendanceRule rule : rules) {
            list.add(toVO(rule, shiftsByStation.getOrDefault(rule.getStationId(), List.of())));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceRuleVO saveRule(AttendanceRuleRequest request) {
        AttendanceRuleRequest safe = request == null ? new AttendanceRuleRequest() : request;
        Long stationId = safe.getStationId();
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        // U-5（已裁定）：时段已由该驿站班次决定，收到非空 checkPeriods 一律拒绝（通用 400，不新增 91xx）
        if (safe.getCheckPeriods() != null && !safe.getCheckPeriods().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时段已由该驿站班次决定，请维护班次");
        }
        validateRule(safe);
        AttendanceRule current = findRule(stationId);
        AttendanceRule payload = normalize(safe);
        AttendanceRule target = current != null ? current : newRuleDefaults(stationId, station);
        applyPayload(target, payload);
        target.setUpdateTime(LocalDateTime.now());
        if (target.getId() == null) {
            attendanceRuleMapper.insert(target);
        } else {
            attendanceRuleMapper.updateById(target);
        }
        return toVO(target);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceRule findRule(Long stationId) {
        if (stationId == null) {
            return null;
        }
        LambdaQueryWrapper<AttendanceRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceRule::getStationId, stationId).orderByAsc(AttendanceRule::getId);
        List<AttendanceRule> list = attendanceRuleMapper.selectList(wrapper);
        // 一驿一条由 Service 保证；取最早一条兜底，避免脏数据导致 selectOne 抛异常
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AttendanceRuleVO toVO(AttendanceRule rule) {
        return toVO(rule, attendanceShiftService.enabledShifts(rule.getStationId()));
    }

    /** 单条规则出参（时段/频次/上下班时间由传入的启用班次派生，避免逐条重复查库） */
    private AttendanceRuleVO toVO(AttendanceRule rule, List<AttendanceShift> enabledShifts) {
        List<AttendancePeriodResolver.ResolvedPeriod> periods = AttendancePeriodResolver.resolveByShifts(
                enabledShifts, algoProperties.getPayroll().getMiddayBoundaryMinute());
        AttendanceRuleVO vo = new AttendanceRuleVO();
        vo.setId(rule.getId());
        vo.setStationId(rule.getStationId());
        vo.setStationName(stationName(rule.getStationId()));
        vo.setRuleName(rule.getRuleName());
        vo.setEnableWifi(toBool(rule.getEnableWifi()));
        vo.setEnableLocation(toBool(rule.getEnableLocation()));
        vo.setEnableTimeWindow(toBool(rule.getEnableTimeWindow()));
        vo.setMatchMode(rule.getMatchMode());
        vo.setWifiList(rule.getWifiList() == null ? new ArrayList<>() : copyWifi(rule.getWifiList()));
        vo.setLongitude(rule.getLongitude());
        vo.setLatitude(rule.getLatitude());
        vo.setRadius(rule.getRadius());
        // 时段 / 频次 / 上下班时间均为「由启用班次派生」的只读值（U-4：保留字段、值改派生）
        vo.setCheckFrequency(periods.size() * 2);
        vo.setCheckPeriods(toCheckPeriods(periods));
        vo.setCheckPeriodsReadonly(true);
        vo.setAllowEarlyMin(rule.getAllowEarlyMin());
        vo.setAllowLateMin(rule.getAllowLateMin());
        vo.setWorkStartTime(AttendancePeriodResolver.firstStartTime(periods));
        vo.setWorkEndTime(AttendancePeriodResolver.lastEndTime(periods));
        vo.setLateThresholdMin(rule.getLateThresholdMin());
        vo.setEarlyLeaveThresholdMin(rule.getEarlyLeaveThresholdMin());
        vo.setStatus(rule.getStatus());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    /** 派生时段 → 出参时段（保持 {@code checkPeriods} 字段形态不变，仅值改由班次派生） */
    private List<CheckPeriod> toCheckPeriods(List<AttendancePeriodResolver.ResolvedPeriod> periods) {
        List<CheckPeriod> list = new ArrayList<>(periods.size());
        for (AttendancePeriodResolver.ResolvedPeriod period : periods) {
            CheckPeriod checkPeriod = new CheckPeriod();
            checkPeriod.setName(period.name());
            checkPeriod.setStartTime(period.startTime());
            checkPeriod.setEndTime(period.endTime());
            list.add(checkPeriod);
        }
        return list;
    }

    // ==================== 校验（文案与顺序逐条对齐 Mock validateRule） ====================

    private void validateRule(AttendanceRuleRequest body) {
        if (body.getRuleName() != null && !AttendanceSupport.textLen(body.getRuleName(), 1, 50)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "规则名称长度须为 1-50");
        }
        if (body.getMatchMode() != null
                && !List.of("ALL", "ANY").contains(body.getMatchMode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "matchMode 仅支持 ALL / ANY");
        }
        // workStartTime / workEndTime 已废弃（真源统一后由班次派生，入参忽略），故不再校验其格式
        if (body.getRadius() != null && !(body.getRadius() > 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "围栏半径须大于 0");
        }
        if (body.getLateThresholdMin() != null && !(body.getLateThresholdMin() >= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "迟到阈值须不小于 0");
        }
        if (body.getEarlyLeaveThresholdMin() != null && !(body.getEarlyLeaveThresholdMin() >= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "早退阈值须不小于 0");
        }
        if (body.getAllowEarlyMin() != null && !(body.getAllowEarlyMin() >= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "允许提前打卡分钟数须不小于 0");
        }
        if (body.getAllowLateMin() != null && !(body.getAllowLateMin() >= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "允许延后打卡分钟数须不小于 0");
        }
        // WiFi 白名单：长度 / MAC 格式 / 重复 / 条数（至多 1 条）四项下沉校验，与前端先行约束对称（防直调 API 绕过）
        String wifiError = AttendanceWifiValidator.validate(body.getWifiList());
        if (wifiError != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, wifiError);
        }
        // 刻意不对「enableWifi=true 且白名单为空」做 fail-closed 阻断：设计规范 §3.4 明确该状态只警告不阻断，
        // 以放行"先开开关、后配 WiFi"的分步配置。差量语义：wifiList=null 表示不提交/沿用现值、[] 表示清空，
        // 二者可区分，服务端有能力据此 fail-closed，但按产品口径刻意不阻断。风险由前端 warning + 仅 ADMIN 可写收敛。
        // 可用性风险：该态下打卡将因 WiFi 未命中失败（9103），见 api.md §8.2 风险登记。
    }

    // ==================== 归一化（白名单写入） ====================

    /**
     * 组装白名单内的写入值（类型在此收口，避免字符串 '0'/'false' 之类写进规则）。
     * <p>
     * <b>真源统一（方案 §5 / §7.1）</b>：{@code checkPeriods} / {@code checkFrequency} / {@code workStartTime}
     * / {@code workEndTime} 均<b>不再写入</b>——时段与上下班时间改由启用班次实时派生，频次只读派生；入参一律忽略。
     */
    private AttendanceRule normalize(AttendanceRuleRequest body) {
        AttendanceRule payload = new AttendanceRule();
        if (body.getRuleName() != null) {
            payload.setRuleName(body.getRuleName().trim());
        }
        if (body.getEnableWifi() != null) {
            payload.setEnableWifi(body.getEnableWifi() ? 1 : 0);
        }
        if (body.getEnableLocation() != null) {
            payload.setEnableLocation(body.getEnableLocation() ? 1 : 0);
        }
        if (body.getEnableTimeWindow() != null) {
            payload.setEnableTimeWindow(body.getEnableTimeWindow() ? 1 : 0);
        }
        if (body.getMatchMode() != null) {
            payload.setMatchMode(body.getMatchMode());
        }
        if (body.getWifiList() != null) {
            // 归一收口到 AttendanceWifiValidator：ssid trim、bssid 空串归一为 null（与校验判据同源，避免口径分叉）
            payload.setWifiList(AttendanceWifiValidator.normalize(body.getWifiList()));
        }
        if (body.getLongitude() != null) {
            payload.setLongitude(body.getLongitude());
        }
        if (body.getLatitude() != null) {
            payload.setLatitude(body.getLatitude());
        }
        if (body.getRadius() != null) {
            payload.setRadius(body.getRadius());
        }
        if (body.getLateThresholdMin() != null) {
            payload.setLateThresholdMin(body.getLateThresholdMin());
        }
        if (body.getEarlyLeaveThresholdMin() != null) {
            payload.setEarlyLeaveThresholdMin(body.getEarlyLeaveThresholdMin());
        }
        if (body.getAllowEarlyMin() != null) {
            payload.setAllowEarlyMin(body.getAllowEarlyMin());
        }
        if (body.getAllowLateMin() != null) {
            payload.setAllowLateMin(body.getAllowLateMin());
        }
        if (body.getStatus() != null) {
            payload.setStatus(body.getStatus());
        }
        return payload;
    }

    /** 白名单：仅把 payload 中非 null 的字段写入目标规则 */
    private void applyPayload(AttendanceRule target, AttendanceRule payload) {
        if (payload.getRuleName() != null) {
            target.setRuleName(payload.getRuleName());
        }
        if (payload.getEnableWifi() != null) {
            target.setEnableWifi(payload.getEnableWifi());
        }
        if (payload.getEnableLocation() != null) {
            target.setEnableLocation(payload.getEnableLocation());
        }
        if (payload.getEnableTimeWindow() != null) {
            target.setEnableTimeWindow(payload.getEnableTimeWindow());
        }
        if (payload.getMatchMode() != null) {
            target.setMatchMode(payload.getMatchMode());
        }
        if (payload.getWifiList() != null) {
            target.setWifiList(payload.getWifiList());
        }
        if (payload.getLongitude() != null) {
            target.setLongitude(payload.getLongitude());
        }
        if (payload.getLatitude() != null) {
            target.setLatitude(payload.getLatitude());
        }
        if (payload.getRadius() != null) {
            target.setRadius(payload.getRadius());
        }
        if (payload.getLateThresholdMin() != null) {
            target.setLateThresholdMin(payload.getLateThresholdMin());
        }
        if (payload.getEarlyLeaveThresholdMin() != null) {
            target.setEarlyLeaveThresholdMin(payload.getEarlyLeaveThresholdMin());
        }
        if (payload.getAllowEarlyMin() != null) {
            target.setAllowEarlyMin(payload.getAllowEarlyMin());
        }
        if (payload.getAllowLateMin() != null) {
            target.setAllowLateMin(payload.getAllowLateMin());
        }
        if (payload.getStatus() != null) {
            target.setStatus(payload.getStatus());
        }
        // 时段 / 频次 / 上下班时间不再写入：真源统一后由启用班次派生（D7/R-14）
    }

    /**
     * 新驿站默认规则（对齐 Mock {@code ruleSeed} 的围栏与阈值部分）。
     * <p>
     * <b>真源统一（方案 §10.3 C3/D8）</b>：不再播种 {@code checkPeriods} / {@code workStartTime} / {@code workEndTime}
     * （时段改由启用班次派生）。若该驿站尚未维护启用班次，规则出参的派生时段为空、频次为 0，前端据此引导先配班次。
     * TODO(扩展): 若产品要求新驿站「开箱即可打卡」，可在此按站点默认排班创建默认班次（需走班次定义侧校验），当前按「提示先配班次」实现。
     */
    private AttendanceRule newRuleDefaults(Long stationId, Station station) {
        double offset = stationId - 1;
        WifiEntry wifi = new WifiEntry();
        wifi.setSsid((station.getCode() == null ? "ST000" : station.getCode()) + "-Express");
        wifi.setBssid("AC:84:C6:00:00:" + String.format("%02d", stationId));

        AttendanceRule rule = new AttendanceRule();
        rule.setStationId(stationId);
        rule.setRuleName((station.getStationName() == null ? "" : station.getStationName()) + "默认打卡规则");
        rule.setEnableWifi(1);
        rule.setEnableLocation(1);
        rule.setEnableTimeWindow(1);
        rule.setMatchMode("ALL");
        rule.setWifiList(new ArrayList<>(List.of(wifi)));
        rule.setLongitude(scale6(FENCE_ORIGIN_LONGITUDE + offset * 0.012));
        rule.setLatitude(scale6(FENCE_ORIGIN_LATITUDE + (offset % 3) * 0.008));
        rule.setRadius(300);
        // check_frequency 列保留但为只读派生占位；此处写默认值仅满足 NOT NULL 结构约束，出参以班次数派生为准
        rule.setCheckFrequency(2);
        rule.setAllowEarlyMin(30);
        rule.setAllowLateMin(60);
        rule.setLateThresholdMin(30);
        rule.setEarlyLeaveThresholdMin(30);
        rule.setStatus(1);
        return rule;
    }

    private BigDecimal scale6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }

    private Boolean toBool(Integer value) {
        return value != null && value == 1;
    }

    private List<WifiEntry> copyWifi(List<WifiEntry> source) {
        List<WifiEntry> list = new ArrayList<>(source.size());
        for (WifiEntry w : source) {
            WifiEntry entry = new WifiEntry();
            entry.setSsid(w.getSsid());
            entry.setBssid(w.getBssid());
            list.add(entry);
        }
        return list;
    }
}
