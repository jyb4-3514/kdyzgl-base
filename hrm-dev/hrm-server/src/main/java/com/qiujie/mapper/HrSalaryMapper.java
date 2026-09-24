package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.HrSalary;

/**
 * 当前定薪 Mapper（db.md §8.5）。员工 1:1，按 employee_id 查。
 */
public interface HrSalaryMapper extends BaseMapper<HrSalary> {
}
