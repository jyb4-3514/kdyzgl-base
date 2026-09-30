package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.LoginUser;
import com.qiujie.common.PageResult;
import com.qiujie.dto.finance.PayrollSettingLogQuery;
import com.qiujie.dto.finance.StationPayrollSettingQuery;
import com.qiujie.dto.finance.StationPayrollSettingSaveRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.entity.StationPayrollSettingLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.StationPayrollSettingLogMapper;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.StationPayrollSettingService;
import com.qiujie.service.finance.support.PayrollSchedulePlanner;
import com.qiujie.service.finance.support.PayrollSettingLogAction;
import com.qiujie.util.JsonUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.finance.PayrollSettingLogVO;
import com.qiujie.vo.finance.StationPayrollSettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 驿站算薪配置服务实现（I-1 / I-2 / I-3 / I-9）。
 * <p>
 * <b>留痕纪律（M-9）</b>：I-3 的配置落库与留痕写入在<b>同一事务</b>内；action 判定见 {@link #resolveAction}。
 * {@code before}/{@code after} 仅写白名单键，绝不 dump 实体或个人信息。
 * <p>
 * 未配置驿站：I-1 以 DDL 默认值填充（列表形态稳定），I-2 返回 {@code 9406}（可判定分支，契约一致）。
 */
@Service
@RequiredArgsConstructor
public class StationPayrollSettingServiceImpl implements StationPayrollSettingService {

    /** DDL 默认值（契约常量，非算法超参；见 V20 station_payroll_setting 列默认） */
    private static final int DEFAULT_ENABLED = 0;
    private static final int DEFAULT_PAYROLL_DAY = 1;
    private static final String DEFAULT_PAYROLL_TIME = "09:00";
    private static final int DEFAULT_NOTIFY_ENABLED = 1;
    /** remark 列长（VARCHAR(255)） */
    private static final int REMARK_MAX = 255;

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StationPayrollSettingMapper settingMapper;
    private final StationPayrollSettingLogMapper settingLogMapper;
    private final StationMapper stationMapper;
    private final EmployeeMapper employeeMapper;

    // ==================== I-1 ====================

    @Override
    @Transactional(readOnly = true)
    public List<StationPayrollSettingVO> list(StationPayrollSettingQuery query) {
        StationPayrollSettingQuery safe = query == null ? new StationPayrollSettingQuery() : query;
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .eq(safe.getStationId() != null, Station::getId, safe.getStationId())
                .orderByAsc(Station::getId));
        if (stations.isEmpty()) {
            return List.of();
        }
        Set<Long> stationIds = stations.stream().map(Station::getId).collect(Collectors.toSet());
        Map<Long, StationPayrollSetting> byStation = new LinkedHashMap<>();
        for (StationPayrollSetting setting : settingMapper.selectList(new LambdaQueryWrapper<StationPayrollSetting>()
                .in(StationPayrollSetting::getStationId, stationIds))) {
            byStation.putIfAbsent(setting.getStationId(), setting);
        }
        List<StationPayrollSettingVO> result = new ArrayList<>(stations.size());
        for (Station station : stations) {
            StationPayrollSettingVO vo = toVO(station, byStation.get(station.getId()));
            // 未配置站按默认 enabled=0 参与筛选（与其展示口径一致）
            if (safe.getEnabled() == null || Objects.equals(safe.getEnabled(), vo.getEnabled())) {
                result.add(vo);
            }
        }
        return result;
    }

    // ==================== I-2 ====================

    @Override
    @Transactional(readOnly = true)
    public StationPayrollSettingVO detail(Long stationId) {
        Station station = requireStation(stationId);
        StationPayrollSetting setting = findSetting(stationId);
        if (setting == null) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_SETTING_NOT_EXISTS);
        }
        return toVO(station, setting);
    }

    // ==================== I-3 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StationPayrollSettingVO save(Long stationId, StationPayrollSettingSaveRequest request) {
        Station station = requireStation(stationId);
        StationPayrollSettingSaveRequest safe = request == null ? new StationPayrollSettingSaveRequest() : request;
        StationPayrollSetting existing = findSetting(stationId);

        Integer enabled = resolveFlag(safe.getEnabled(), existing == null ? DEFAULT_ENABLED : existing.getEnabled(), "enabled");
        Integer notifyEnabled = resolveFlag(safe.getNotifyEnabled(),
                existing == null ? DEFAULT_NOTIFY_ENABLED : existing.getNotifyEnabled(), "notifyEnabled");
        Integer payrollDay = safe.getPayrollDay() != null ? safe.getPayrollDay()
                : (existing == null ? DEFAULT_PAYROLL_DAY : existing.getPayrollDay());
        if (!PayrollSchedulePlanner.isValidDay(payrollDay)) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_SETTING_DAY_INVALID);
        }
        String payrollTime = safe.getPayrollTime() != null ? safe.getPayrollTime().trim()
                : (existing == null ? DEFAULT_PAYROLL_TIME : existing.getPayrollTime());
        if (!PayrollSchedulePlanner.isValidTime(payrollTime)) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_SETTING_TIME_INVALID);
        }
        String remark = safe.getRemark() == null ? (existing == null ? null : existing.getRemark()) : safe.getRemark().trim();
        if (remark != null && remark.length() > REMARK_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "备注长度不超过 " + REMARK_MAX + " 字");
        }

        String action = resolveAction(existing, enabled);
        Object before = existing == null ? null : snapshot(existing);

        StationPayrollSetting setting = existing == null ? new StationPayrollSetting() : existing;
        setting.setStationId(stationId);
        setting.setEnabled(enabled);
        setting.setPayrollDay(payrollDay);
        setting.setPayrollTime(payrollTime);
        setting.setNotifyEnabled(notifyEnabled);
        setting.setRemark(remark);
        if (existing == null) {
            settingMapper.insert(setting);
        } else {
            settingMapper.updateById(setting);
        }

        writeSettingLog(stationId, action, before, snapshot(setting));
        return toVO(station, setting);
    }

    // ==================== I-9 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<PayrollSettingLogVO> logs(Long stationId, PayrollSettingLogQuery query) {
        requireStation(stationId);
        PayrollSettingLogQuery safe = query == null ? new PayrollSettingLogQuery() : query;
        LocalDateTime start = parseTimeBound(safe.getStartTime(), true);
        LocalDateTime end = parseTimeBound(safe.getEndTime(), false);
        LambdaQueryWrapper<StationPayrollSettingLog> wrapper = new LambdaQueryWrapper<StationPayrollSettingLog>()
                .eq(StationPayrollSettingLog::getStationId, stationId)
                .ge(start != null, StationPayrollSettingLog::getTime, start)
                .le(end != null, StationPayrollSettingLog::getTime, end)
                .orderByDesc(StationPayrollSettingLog::getTime)
                .orderByDesc(StationPayrollSettingLog::getId);
        Page<StationPayrollSettingLog> page = settingLogMapper.selectPage(
                new Page<>(safe.getPageNum(), safe.getPageSize()), wrapper);
        List<PayrollSettingLogVO> list = new ArrayList<>(page.getRecords().size());
        for (StationPayrollSettingLog row : page.getRecords()) {
            list.add(toLogVO(row));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    // ==================== 私有：查询 / 转换 / 留痕 ====================

    private Station requireStation(Long stationId) {
        Station station = stationId == null ? null : stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        return station;
    }

    /** 一驿一条：取首条（活跃唯一由 Service 保证；@TableLogic 自动过滤已删） */
    private StationPayrollSetting findSetting(Long stationId) {
        if (stationId == null) {
            return null;
        }
        List<StationPayrollSetting> rows = settingMapper.selectList(new LambdaQueryWrapper<StationPayrollSetting>()
                .eq(StationPayrollSetting::getStationId, stationId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * action 判定（M-9，优先级：CREATE &gt; ENABLE/DISABLE &gt; UPDATE）。
     * <p>首次创建记 {@code CREATE}（before 为空，after 已可追溯初始 enabled）。
     */
    private String resolveAction(StationPayrollSetting existing, Integer newEnabled) {
        if (existing == null) {
            return PayrollSettingLogAction.CREATE;
        }
        boolean wasEnabled = Objects.equals(1, existing.getEnabled());
        boolean willEnabled = Objects.equals(1, newEnabled);
        if (!wasEnabled && willEnabled) {
            return PayrollSettingLogAction.ENABLE;
        }
        if (wasEnabled && !willEnabled) {
            return PayrollSettingLogAction.DISABLE;
        }
        return PayrollSettingLogAction.UPDATE;
    }

    /** 白名单快照（键序稳定；不含 stationId 与操作人，二者已单列） */
    private Map<String, Object> snapshot(StationPayrollSetting setting) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", setting.getEnabled());
        map.put("payrollDay", setting.getPayrollDay());
        map.put("payrollTime", setting.getPayrollTime());
        map.put("notifyEnabled", setting.getNotifyEnabled());
        map.put("remark", setting.getRemark());
        return map;
    }

    /** 追加 1 条配置留痕（人工链路时间用 LocalDateTime.now()，与调度链路区分） */
    private void writeSettingLog(Long stationId, String action, Object before, Object after) {
        LoginUser user = UserContext.get();
        Long operatorId = user == null ? null : user.getUserId();
        String operatorName = null;
        if (operatorId != null) {
            Employee employee = employeeMapper.selectById(operatorId);
            operatorName = employee == null ? null : employee.getRealName();
        }
        if (operatorName == null && user != null) {
            operatorName = user.getUsername();
        }
        StationPayrollSettingLog row = new StationPayrollSettingLog();
        row.setStationId(stationId);
        row.setAction(action);
        row.setOperatorId(operatorId);
        row.setOperatorName(operatorName);
        row.setOperatorRole(user == null ? null : user.getRole());
        row.setBefore(JsonUtil.write(before));
        row.setAfter(JsonUtil.write(after));
        row.setTime(LocalDateTime.now());
        settingLogMapper.insert(row);
    }

    private StationPayrollSettingVO toVO(Station station, StationPayrollSetting setting) {
        StationPayrollSettingVO vo = new StationPayrollSettingVO();
        vo.setStationId(station.getId());
        vo.setStationName(station.getStationName());
        if (setting == null) {
            // 未配置站：以 DDL 默认值填充，保证列表形态稳定（I-1）
            vo.setEnabled(DEFAULT_ENABLED);
            vo.setPayrollDay(DEFAULT_PAYROLL_DAY);
            vo.setPayrollTime(DEFAULT_PAYROLL_TIME);
            vo.setNotifyEnabled(DEFAULT_NOTIFY_ENABLED);
            return vo;
        }
        vo.setEnabled(setting.getEnabled());
        vo.setPayrollDay(setting.getPayrollDay());
        vo.setPayrollTime(setting.getPayrollTime());
        vo.setNotifyEnabled(setting.getNotifyEnabled());
        vo.setRemark(setting.getRemark());
        vo.setUpdateTime(setting.getUpdateTime());
        return vo;
    }

    private PayrollSettingLogVO toLogVO(StationPayrollSettingLog row) {
        PayrollSettingLogVO vo = new PayrollSettingLogVO();
        vo.setId(row.getId());
        vo.setStationId(row.getStationId());
        vo.setAction(row.getAction());
        vo.setOperatorId(row.getOperatorId());
        vo.setOperatorName(row.getOperatorName());
        vo.setOperatorRole(row.getOperatorRole());
        vo.setBefore(JsonUtil.read(row.getBefore(), JsonUtil.mapType()));
        vo.setAfter(JsonUtil.read(row.getAfter(), JsonUtil.mapType()));
        vo.setTime(row.getTime());
        vo.setRemark(row.getRemark());
        return vo;
    }

    /** 0/1 开关取值收敛；非空且非法 → 400 */
    private Integer resolveFlag(Integer value, Integer fallback, String field) {
        Integer resolved = value == null ? fallback : value;
        if (resolved == null) {
            resolved = 0;
        }
        if (resolved != 0 && resolved != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 取值须为 0 或 1");
        }
        return resolved;
    }

    /**
     * 时间边界解析：支持 {@code yyyy-MM-dd}（dateOnly 起用 00:00:00、止用 23:59:59）与
     * {@code yyyy-MM-dd HH:mm:ss}；非法 → 400。
     */
    private LocalDateTime parseTimeBound(String text, boolean startOfDay) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDate date = LocalDate.parse(trimmed);
                return startOfDay ? date.atStartOfDay() : date.atTime(LocalTime.MAX.withNano(0));
            }
            return LocalDateTime.parse(trimmed, DATETIME_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间格式非法（须为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）");
        }
    }
}
