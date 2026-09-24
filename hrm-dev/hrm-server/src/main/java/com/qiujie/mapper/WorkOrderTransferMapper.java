package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.WorkOrderTransfer;

/**
 * 工单转单留痕 Mapper（db.md §8.8.3）。追加型：仅插入与按工单查询，不提供更新 / 删除。
 */
public interface WorkOrderTransferMapper extends BaseMapper<WorkOrderTransfer> {
}
