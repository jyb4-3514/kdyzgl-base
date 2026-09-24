package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.HrFlow;

/**
 * 入职/离职流程 Mapper（db.md §8.5）。按 (flow_type, status) 列表过滤、flow_no 查重。
 */
public interface HrFlowMapper extends BaseMapper<HrFlow> {
}
