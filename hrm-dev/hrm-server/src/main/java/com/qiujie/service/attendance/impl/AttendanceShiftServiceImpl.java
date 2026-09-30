package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceShiftRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.attendance.support.AttendancePeriodResolver;
import com.qiujie.service.attendance.support.AttendanceSupport;
import com.qiujie.vo.attendance.AttendanceShiftVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 班次服务实现（Mock {@code attendanceStore} 班次段 + {@code routes/attendance.js} 班次段）。
 * <p>
 * 删除保护：被排班引用时拒绝删除（删掉会让历史排班指向空班次，打卡判定失去时间基准）。
 * <p>
 * <b>真源统一（方案 v1.2）</b>：班次是打卡时间的唯一真源；新增班次定义侧校验——① 班次名不得撞保留名「全天班」；
 * ② 站点启用班次数 ≤ 2；③ 启用班次时段归属（{@code ordinal}）互异（否则计薪单元串号）。违规回 {@code 9114}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceShiftServiceImpl implements AttendanceShiftService {

    /**
     * 站点启用班次上限：计薪单元编码 {@code epochDay×2+ordinal} 仅容纳 0/1（单日两个半天单元），
     * 属结构不变式而非可调超参，故固化为常量（对齐排班侧 {@code PAYROLL_SHIFT_UNIT_CAPACITY}）。
     */
    private static final int MAX_ENABLED_SHIFTS = 2;

    private final AttendanceShiftMapper attendanceShiftMapper;
    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final StationMapper stationMapper;
    /** 算法参数：班次序号界值 / 计薪保留哨兵名（班次定义侧校验用） */
    private final AlgoProperties algoProperties;

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceShiftVO> list(Long stationId) {
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        // 管理页需展示全部班次（含停用），故此处不过滤 status；派生集合走 enabledShifts
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceShift::getStationId, stationId);
        List<AttendanceShift> rows = new ArrayList<>(attendanceShiftMapper.selectList(wrapper));
        sortByStart(rows);
        List<AttendanceShiftVO> list = new ArrayList<>(rows.size());
        for (AttendanceShift shift : rows) {
            list.add(toVO(shift));
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceShift> enabledShifts(Long stationId) {
        if (stationId == null) {
            return List.of();
        }
        return listByStation(stationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<AttendanceShift>> enabledShiftsByStation(Collection<Long> stationIds) {
        Map<Long, List<AttendanceShift>> result = new LinkedHashMap<>();
        if (stationIds == null || stationIds.isEmpty()) {
            return result;
        }
        Set<Long> ids = new HashSet<>();
        for (Long id : stationIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        // status=1 且未软删（@TableLogic 自动追加 is_deleted=0）
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceShift::getStatus, 1).in(AttendanceShift::getStationId, ids);
        List<AttendanceShift> rows = attendanceShiftMapper.selectList(wrapper);
        sortByStart(rows);
        for (AttendanceShift shift : rows) {
            result.computeIfAbsent(shift.getStationId(), k -> new ArrayList<>()).add(shift);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceShiftVO create(AttendanceShiftRequest request) {
        AttendanceShiftRequest safe = request == null ? new AttendanceShiftRequest() : request;
        Long stationId = safe.getStationId();
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        if (stationMapper.selectById(stationId) == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        validateShift(safe);
        validateShiftSet(stationId, null, safe);
        AttendanceShift shift = new AttendanceShift();
        shift.setStationId(stationId);
        shift.setShiftName(safe.getShiftName().trim());
        shift.setStartTime(safe.getStartTime());
        shift.setEndTime(safe.getEndTime());
        shift.setColor(safe.getColor().toUpperCase());
        shift.setRestMinutes(safe.getRestMinutes() == null ? 0 : safe.getRestMinutes());
        shift.setStatus(safe.getStatus() == null ? 1 : safe.getStatus());
        attendanceShiftMapper.insert(shift);
        return toVO(shift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceShiftVO update(Long id, AttendanceShiftRequest request) {
        AttendanceShiftRequest safe = request == null ? new AttendanceShiftRequest() : request;
        AttendanceShift shift = attendanceShiftMapper.selectById(id);
        if (shift == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班次不存在");
        }
        validateShift(safe);
        validateShiftSet(shift.getStationId(), id, safe);
        shift.setShiftName(safe.getShiftName().trim());
        shift.setStartTime(safe.getStartTime());
        shift.setEndTime(safe.getEndTime());
        shift.setColor(safe.getColor().toUpperCase());
        shift.setRestMinutes(safe.getRestMinutes() == null ? 0 : safe.getRestMinutes());
        shift.setStatus(safe.getStatus() == null ? 1 : safe.getStatus());
        attendanceShiftMapper.updateById(shift);
        return toVO(shift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AttendanceShift shift = attendanceShiftMapper.selectById(id);
        if (shift == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班次不存在");
        }
        LambdaQueryWrapper<AttendanceSchedule> inUse = new LambdaQueryWrapper<>();
        inUse.select(AttendanceSchedule::getId).eq(AttendanceSchedule::getShiftId, id);
        if (attendanceScheduleMapper.selectCount(inUse) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该班次已被排班引用，不能删除");
        }
        attendanceShiftMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceShift findEntity(Long id) {
        return id == null ? null : attendanceShiftMapper.selectById(id);
    }

    @Override
    public AttendanceShiftVO toVO(AttendanceShift shift) {
        AttendanceShiftVO vo = new AttendanceShiftVO();
        vo.setId(shift.getId());
        vo.setStationId(shift.getStationId());
        vo.setStationName(stationName(shift.getStationId()));
        vo.setShiftName(shift.getShiftName());
        vo.setStartTime(shift.getStartTime());
        vo.setEndTime(shift.getEndTime());
        vo.setColor(shift.getColor());
        vo.setRestMinutes(shift.getRestMinutes());
        vo.setStatus(shift.getStatus());
        return vo;
    }

    @Override
    public AttendanceShiftVO defaultShift(AttendanceRule rule) {
        // 兜底班次改为「该驿站首个启用班次」派生（方案 §8.4 / §10.7）；无启用班次 → null（前端置无班次空态）
        if (rule == null || rule.getStationId() == null) {
            return null;
        }
        List<AttendanceShift> enabled = enabledShifts(rule.getStationId());
        if (enabled.isEmpty()) {
            return null;
        }
        AttendanceShift first = enabled.get(0);
        AttendanceShiftVO vo = new AttendanceShiftVO();
        vo.setId(null);
        vo.setStationId(first.getStationId());
        vo.setStationName(stationName(first.getStationId()));
        vo.setShiftName(first.getShiftName());
        vo.setStartTime(first.getStartTime());
        vo.setEndTime(first.getEndTime());
        vo.setColor(first.getColor() == null ? AttendanceConstants.DEFAULT_SHIFT_COLOR : first.getColor());
        vo.setRestMinutes(first.getRestMinutes() == null ? 0 : first.getRestMinutes());
        vo.setStatus(1);
        return vo;
    }

    // ==================== 私有方法 ====================

    /** 该驿站启用班次实体（status=1 且未软删），按开始时间升序 */
    private List<AttendanceShift> listByStation(Long stationId) {
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceShift::getStationId, stationId).eq(AttendanceShift::getStatus, 1);
        List<AttendanceShift> rows = new ArrayList<>(attendanceShiftMapper.selectList(wrapper));
        sortByStart(rows);
        return rows;
    }

    /** 按开始时间（分钟数）升序，避免依赖字符串排序与「24:00」边界 */
    private void sortByStart(List<AttendanceShift> rows) {
        rows.sort(Comparator.comparingDouble(s -> AttendancePeriodResolver.minutesOfDay(s.getStartTime())));
    }

    /** 校验（文案与顺序逐条对齐 Mock validateShift） */
    private void validateShift(AttendanceShiftRequest body) {
        // 首查（N2）：归一化（trim）后不得撞计薪保留哨兵名，防新记录被三态哨兵分支误判（方案 §6.4）
        // 注意：本方法在 setShiftName(trim()) 之前调用，故必须自行 trim 后再比对，与落库值同源归一
        String normalizedName = body.getShiftName() == null ? null : body.getShiftName().trim();
        if (isReservedShiftName(normalizedName)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_DEFINITION_INVALID,
                    "班次名不得使用保留名「" + normalizedName + "」");
        }
        if (!AttendanceSupport.textLen(body.getShiftName(), 1, 20)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "班次名称长度须为 1-20");
        }
        if (!AttendanceSupport.isClock(body.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间格式须为 HH:mm");
        }
        if (!AttendanceSupport.isEndClock(body.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间格式须为 HH:mm");
        }
        if (AttendancePeriodResolver.minutesOfDay(body.getStartTime())
                >= AttendancePeriodResolver.minutesOfDay(body.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间须晚于开始时间");
        }
        if (!AttendanceSupport.isHexColor(body.getColor())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "班次颜色须为 #RRGGBB");
        }
        if (body.getRestMinutes() != null && !(body.getRestMinutes() >= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "休息时长须不小于 0");
        }
    }

    /** 归一化后是否撞保留名：计薪哨兵名（配置）或默认时段名常量（仅 trim，不做全角/大小写折叠，与计薪侧精确 equals 对齐） */
    private boolean isReservedShiftName(String trimmedName) {
        if (trimmedName == null) {
            return false;
        }
        String sentinel = algoProperties.getPayroll().getLegacyPeriodSentinel();
        return (sentinel != null && sentinel.equals(trimmedName))
                || AttendanceConstants.DEFAULT_PERIOD_NAME.equals(trimmedName);
    }

    /**
     * 班次定义侧集合校验：站点启用班次数 ≤ 2 且时段归属（{@code ordinal}）互异（方案 §3.2 / §6.3，U-6 回 9114）。
     * <p>计薪单元编码仅容纳「一早一晚」两个半天单元，超限或同半天均会使折算串号/去重少算，故在定义侧拦截。
     *
     * @param currentShiftId 编辑场景下待排除的当前班次 id；新增传 {@code null}
     */
    private void validateShiftSet(Long stationId, Long currentShiftId, AttendanceShiftRequest body) {
        int boundary = algoProperties.getPayroll().getMiddayBoundaryMinute();
        Set<Integer> ordinals = new HashSet<>();
        int count = 0;
        for (AttendanceShift shift : enabledShifts(stationId)) {
            if (currentShiftId != null && currentShiftId.equals(shift.getId())) {
                continue;
            }
            count++;
            int ordinal = AttendancePeriodResolver.shiftOrdinal(shift.getStartTime(), boundary);
            if (ordinal >= 0) {
                ordinals.add(ordinal);
            }
        }
        int status = body.getStatus() == null ? 1 : body.getStatus();
        if (status == 1) {
            count++;
            int ordinal = AttendancePeriodResolver.shiftOrdinal(body.getStartTime(), boundary);
            if (ordinal >= 0 && !ordinals.add(ordinal)) {
                throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_DEFINITION_INVALID,
                        "启用班次须一早一晚：新班次与已有班次时段归属相同");
            }
        }
        if (count > MAX_ENABLED_SHIFTS) {
            throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_DEFINITION_INVALID,
                    "启用班次最多 " + MAX_ENABLED_SHIFTS + " 个（当前将达 " + count + " 个）");
        }
    }

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }
}
