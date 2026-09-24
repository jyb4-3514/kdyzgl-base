package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.AttendanceSchedule;

/**
 * 排班 Mapper（db.md §8.3.3）。周矩阵/我的排班/逐日统计的过滤条件由 Service 构造。
 */
public interface AttendanceScheduleMapper extends BaseMapper<AttendanceSchedule> {
}
