package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.PayrollItem;

/**
 * 工资单明细 Mapper（db.md §8.6.4）。按 (payroll_id, sort_order) 取明细。
 */
public interface PayrollItemMapper extends BaseMapper<PayrollItem> {
}
