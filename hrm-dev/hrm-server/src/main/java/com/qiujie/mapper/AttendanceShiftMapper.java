package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.AttendanceShift;

/**
 * 班次 Mapper（db.md §8.3.2）。按 stationId 检索与「被排班引用」判定由 Service 构造。
 */
public interface AttendanceShiftMapper extends BaseMapper<AttendanceShift> {
}
