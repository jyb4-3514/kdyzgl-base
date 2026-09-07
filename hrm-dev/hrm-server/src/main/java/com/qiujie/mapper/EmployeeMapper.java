package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Employee;

/**
 * 员工 Mapper。唯一性/归属校验等业务规则由 Service 层实现（决策 D6/D7）。
 */
public interface EmployeeMapper extends BaseMapper<Employee> {
}
