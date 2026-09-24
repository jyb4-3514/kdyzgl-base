package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Comparator;
import java.util.List;

/**
 * 班次服务实现（Mock {@code attendanceStore} 班次段 + {@code routes/attendance.js} 班次段）。
 * <p>
 * 删除保护：被排班引用时拒绝删除（删掉会让历史排班指向空班次，打卡判定失去时间基准）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceShiftServiceImpl implements AttendanceShiftService {

    private final AttendanceShiftMapper attendanceShiftMapper;
    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final StationMapper stationMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceShiftVO> list(Long stationId) {
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceShift::getStationId, stationId);
        List<AttendanceShift> rows = attendanceShiftMapper.selectList(wrapper);
        // 按开始时间（分钟数）升序，避免依赖字符串排序与「24:00」边界
        rows.sort(Comparator.comparingDouble(s -> AttendancePeriodResolver.minutesOfDay(s.getStartTime())));
        List<AttendanceShiftVO> list = new ArrayList<>(rows.size());
        for (AttendanceShift shift : rows) {
            list.add(toVO(shift));
        }
        return list;
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
        AttendanceShiftVO vo = new AttendanceShiftVO();
        vo.setId(null);
        vo.setStationId(rule.getStationId());
        vo.setStationName(stationName(rule.getStationId()));
        vo.setShiftName(AttendanceConstants.DEFAULT_SHIFT_NAME);
        vo.setStartTime(rule.getWorkStartTime());
        vo.setEndTime(rule.getWorkEndTime());
        vo.setColor(AttendanceConstants.DEFAULT_SHIFT_COLOR);
        vo.setRestMinutes(0);
        vo.setStatus(1);
        return vo;
    }

    // ==================== 私有方法 ====================

    /** 校验（文案与顺序逐条对齐 Mock validateShift） */
    private void validateShift(AttendanceShiftRequest body) {
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

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }
}
