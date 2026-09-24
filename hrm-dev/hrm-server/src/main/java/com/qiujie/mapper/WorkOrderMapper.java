package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.WorkOrder;

/**
 * 工单主表 Mapper（db.md §8.8.1）。逻辑删除由 {@code @TableLogic} 统一处理，无需手写 SQL。
 */
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}
