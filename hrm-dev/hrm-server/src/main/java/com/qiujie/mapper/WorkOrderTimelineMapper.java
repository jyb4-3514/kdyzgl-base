package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.WorkOrderTimeline;

/**
 * 工单处理时间线 Mapper（db.md §8.8.2）。追加型：仅插入与按工单查询，不提供更新 / 删除。
 */
public interface WorkOrderTimelineMapper extends BaseMapper<WorkOrderTimeline> {
}
