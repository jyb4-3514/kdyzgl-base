package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Payroll;

/**
 * 工资单 Mapper（db.md §8.6.3）。
 * <p>
 * 列表按 (month, status) / (month, station_id)、详情与幂等按 (employee_id, month, bill_type) 走索引；
 * 计数走 {@code selectMaps} + {@code group by status}（显式列，禁止 SELECT *）。
 */
public interface PayrollMapper extends BaseMapper<Payroll> {
}
