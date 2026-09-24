package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.HrFlowStep;

/**
 * 流程步骤 Mapper（db.md §8.5）。按 flow_id 取步骤（step_order 升序）。
 */
public interface HrFlowStepMapper extends BaseMapper<HrFlowStep> {
}
