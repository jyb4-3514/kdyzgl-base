package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.AttendanceRecord;

/**
 * 打卡记录 Mapper（db.md §8.3.4）。列表/概况/明细/去重槽位查询由 Service 构造。
 */
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
}
