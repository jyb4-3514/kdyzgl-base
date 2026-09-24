package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.PayrollRuleItem;

/**
 * 计薪规则项 Mapper（db.md §8.6.2）。按 (rule_id, sort_order) 取项。
 */
public interface PayrollRuleItemMapper extends BaseMapper<PayrollRuleItem> {
}
