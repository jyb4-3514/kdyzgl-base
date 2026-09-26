package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.dto.attendance.AttendanceRuleRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.CheckPeriod;
import com.qiujie.entity.Station;
import com.qiujie.entity.WifiEntry;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceRuleMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceRuleService;
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
import java.util.List;

/**
 * 打卡规则服务实现（Mock {@code attendanceStore} 规则段 + {@code routes/attendance.js} 规则段）。
 * <p>
 * 白名单写入（防越权改归属/时间）；{@code checkPeriods} 是唯一真源，保存时重算 {@code workStartTime/workEndTime}
 * 派生值；新驿站首存即创建并套用与 Mock 等价的默认规则（否则新驿站直接配规则会得到空时段，打卡判定失效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRuleServiceImpl implements AttendanceRuleService {

    /** 双时段预设：中间留午休，避免两段首尾相接被误判为同一段 */
    private static final List<String[]> DUAL_PERIOD_SEED = List.of(
            new String[]{"上午班", "08:00", "12:00"},
            new String[]{"下午班", "14:00", "18:00"});
    /** 单时段预设 */
    private static final String[] SINGLE_PERIOD_SEED = {"全天班", "08:00", "18:00"};
    /** 启用双时段（4 次打卡）的驿站 id（与 Mock {@code DUAL_FREQUENCY_STATIONS} 一致） */
    private static final List<Long> DUAL_FREQUENCY_STATIONS = List.of(2L, 3L);
    /** 电子围栏原点（示例市内虚构坐标，与 Mock {@code FENCE_ORIGIN} 一致；非真实地点） */
    private static final double FENCE_ORIGIN_LONGITUDE = 117.201;
    private static final double FENCE_ORIGIN_LATITUDE = 31.821;

    private final AttendanceRuleMapper attendanceRuleMapper;
    private final StationMapper stationMapper;

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
        List<AttendanceRuleVO> list = new ArrayList<>();
        for (AttendanceRule rule : attendanceRuleMapper.selectList(wrapper)) {
            list.add(toVO(rule));
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
        validateRule(safe);
        AttendanceRule current = findRule(stationId);
        AttendanceRule payload = normalize(safe, current);
        // 归一化后二次校验：旧客户端只发上下班时间时，时段是被映射出来的，必须仍自洽
        validatePeriodRule(payload.getCheckFrequency(), payload.getCheckPeriods());

        AttendanceRule target = current != null ? current : newRuleDefaults(stationId, station);
        applyPayload(target, payload);
        if (target.getCheckPeriods() != null && !target.getCheckPeriods().isEmpty()) {
            target.setWorkStartTime(target.getCheckPeriods().get(0).getStartTime());
            target.setWorkEndTime(target.getCheckPeriods().get(target.getCheckPeriods().size() - 1).getEndTime());
        }
        LocalDateTime now = LocalDateTime.now();
        target.setUpdateTime(now);
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
        vo.setCheckFrequency(rule.getCheckFrequency());
        vo.setCheckPeriods(rule.getCheckPeriods() == null ? new ArrayList<>() : copyPeriods(rule.getCheckPeriods()));
        vo.setAllowEarlyMin(rule.getAllowEarlyMin());
        vo.setAllowLateMin(rule.getAllowLateMin());
        vo.setWorkStartTime(rule.getWorkStartTime());
        vo.setWorkEndTime(rule.getWorkEndTime());
        vo.setLateThresholdMin(rule.getLateThresholdMin());
        vo.setEarlyLeaveThresholdMin(rule.getEarlyLeaveThresholdMin());
        vo.setStatus(rule.getStatus());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
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
        if (body.getWorkStartTime() != null && !AttendanceSupport.isClock(body.getWorkStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上班时间格式须为 HH:mm");
        }
        if (body.getWorkEndTime() != null && !AttendanceSupport.isEndClock(body.getWorkEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下班时间格式须为 HH:mm");
        }
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

    /**
     * 时段配置校验：频次档位、时段数量、单段起止、段间重叠与顺序。
     * 统一返回 9107（属「时段」维度，前端见码即跳时段配置区；具体哪条不合法由 message 说清）。
     */
    private void validatePeriodRule(Integer frequency, List<CheckPeriod> periods) {
        if (frequency != null && frequency != 2 && frequency != 4) {
            throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND, "checkFrequency 仅支持 2 或 4");
        }
        if (periods == null) {
            return;
        }
        if (periods.isEmpty()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND, "checkPeriods 须为非空数组");
        }
        if (frequency != null && periods.size() != frequency / 2) {
            throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND,
                    "checkPeriods 长度须等于 checkFrequency / 2（本次应为 " + (frequency / 2) + "）");
        }
        double prevEnd = -1;
        for (CheckPeriod period : periods) {
            String name = period != null && period.getName() != null ? "「" + period.getName() + "」" : "";
            if (period == null || !AttendanceSupport.textLen(period.getName(), 1, 20)) {
                throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND, "时段名称长度须为 1-20");
            }
            if (!AttendanceSupport.isClock(period.getStartTime())
                    || !AttendanceSupport.isEndClock(period.getEndTime())) {
                throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND,
                        name + "起止时间格式须为 HH:mm");
            }
            double start = AttendancePeriodResolver.minutesOfDay(period.getStartTime());
            double end = AttendancePeriodResolver.minutesOfDay(period.getEndTime());
            if (start >= end) {
                throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND, name + "的结束时间须晚于开始时间");
            }
            if (start < prevEnd) {
                throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND,
                        "打卡时段之间不允许重叠，且须按开始时间升序");
            }
            prevEnd = end;
        }
    }

    // ==================== 归一化（白名单写入） ====================

    /** 组装白名单内的写入值（类型在此收口，避免字符串 '0'/'false' 之类写进规则） */
    private AttendanceRule normalize(AttendanceRuleRequest body, AttendanceRule current) {
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
        if (body.getWorkStartTime() != null) {
            payload.setWorkStartTime(body.getWorkStartTime());
        }
        if (body.getWorkEndTime() != null) {
            payload.setWorkEndTime(body.getWorkEndTime());
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

        // 时段是唯一真源：未提交时段时沿用现值（避免「只改围栏半径」把时段清空）
        List<CheckPeriod> basePeriods;
        if (body.getCheckPeriods() != null) {
            basePeriods = copyPeriods(body.getCheckPeriods());
        } else if (current != null && current.getCheckPeriods() != null) {
            basePeriods = copyPeriods(current.getCheckPeriods());
        } else {
            basePeriods = null;
        }
        // 兼容旧客户端：只发上下班时间时映射到首/末时段（否则改了时间保存后被派生值覆盖回原样）
        // TODO(扩展): 老板端规则页支持多时段编辑后，删除这条兼容映射。
        if (body.getCheckPeriods() == null && basePeriods != null && !basePeriods.isEmpty()) {
            if (body.getWorkStartTime() != null) {
                basePeriods.get(0).setStartTime(body.getWorkStartTime());
            }
            if (body.getWorkEndTime() != null) {
                basePeriods.get(basePeriods.size() - 1).setEndTime(body.getWorkEndTime());
            }
        }
        if (basePeriods != null) {
            payload.setCheckPeriods(basePeriods);
            payload.setCheckFrequency(body.getCheckFrequency() != null
                    ? body.getCheckFrequency()
                    : (current != null ? current.getCheckFrequency() : 2));
        } else if (body.getCheckFrequency() != null) {
            payload.setCheckFrequency(body.getCheckFrequency());
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
        if (payload.getWorkStartTime() != null) {
            target.setWorkStartTime(payload.getWorkStartTime());
        }
        if (payload.getWorkEndTime() != null) {
            target.setWorkEndTime(payload.getWorkEndTime());
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
        if (payload.getCheckFrequency() != null) {
            target.setCheckFrequency(payload.getCheckFrequency());
        }
        if (payload.getCheckPeriods() != null) {
            target.setCheckPeriods(payload.getCheckPeriods());
        }
    }

    /**
     * 新驿站默认规则（对齐 Mock {@code ruleSeed}）：新驿站建完可直接配规则，且默认时段与围栏自洽。
     * 若不复刻默认值，新建规则将因时段为空而使打卡时间判定失效（兜底时段回退到空起止）。
     */
    private AttendanceRule newRuleDefaults(Long stationId, Station station) {
        long sid = stationId;
        int checkFrequency = DUAL_FREQUENCY_STATIONS.contains(sid) ? 4 : 2;
        List<CheckPeriod> periods = new ArrayList<>();
        if (checkFrequency == 4) {
            for (String[] seed : DUAL_PERIOD_SEED) {
                CheckPeriod period = new CheckPeriod();
                period.setName(seed[0]);
                period.setStartTime(seed[1]);
                period.setEndTime(seed[2]);
                periods.add(period);
            }
        } else {
            CheckPeriod period = new CheckPeriod();
            period.setName(SINGLE_PERIOD_SEED[0]);
            period.setStartTime(SINGLE_PERIOD_SEED[1]);
            period.setEndTime(SINGLE_PERIOD_SEED[2]);
            periods.add(period);
        }
        double offset = sid - 1;
        WifiEntry wifi = new WifiEntry();
        wifi.setSsid((station.getCode() == null ? "ST000" : station.getCode()) + "-Express");
        wifi.setBssid("AC:84:C6:00:00:" + String.format("%02d", sid));

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
        rule.setCheckFrequency(checkFrequency);
        rule.setCheckPeriods(periods);
        rule.setAllowEarlyMin(30);
        rule.setAllowLateMin(60);
        rule.setWorkStartTime(periods.get(0).getStartTime());
        rule.setWorkEndTime(periods.get(periods.size() - 1).getEndTime());
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

    private List<CheckPeriod> copyPeriods(List<CheckPeriod> source) {
        List<CheckPeriod> list = new ArrayList<>(source.size());
        for (CheckPeriod p : source) {
            // 保留 null 项（不静默丢弃），使 validatePeriodRule 能对「非空数组含非法项」统一报时段错误
            if (p == null) {
                list.add(null);
                continue;
            }
            CheckPeriod period = new CheckPeriod();
            period.setName(p.getName() == null ? null : p.getName().trim());
            period.setStartTime(p.getStartTime());
            period.setEndTime(p.getEndTime());
            list.add(period);
        }
        return list;
    }
}
