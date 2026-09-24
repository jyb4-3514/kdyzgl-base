package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.AttendanceRule;

/**
 * 打卡规则 Mapper（db.md §8.3.1）。过滤条件（stationId 定位、活跃唯一判定）由 Service 构造。
 */
public interface AttendanceRuleMapper extends BaseMapper<AttendanceRule> {
}
