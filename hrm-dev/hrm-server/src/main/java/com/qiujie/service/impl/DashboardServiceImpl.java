package com.qiujie.service.impl;

import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.LoginLogMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.DashboardService;
import com.qiujie.vo.DashboardVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 看板服务实现（api.md 4.2，统计口径与文档 SQL 一致）：
 * - 员工/驿站/部门总数：未删除计数（含禁用/停用）；
 * - 今日登录数：login_result=1 且 login_time >= 今日 0 点的去重员工数（走 login_time 索引）。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final DepartmentMapper departmentMapper;
    private final LoginLogMapper loginLogMapper;

    @Override
    public DashboardVO summary() {
        DashboardVO vo = new DashboardVO();
        // selectCount 传入 null wrapper 由 MyBatis-Plus 拼接逻辑删除条件 is_deleted=0
        vo.setEmployeeTotal(employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()));
        vo.setStationTotal(stationMapper.selectCount(new LambdaQueryWrapper<Station>()));
        vo.setDepartmentTotal(departmentMapper.selectCount(new LambdaQueryWrapper<Department>()));
        vo.setTodayLoginCount(loginLogMapper.countTodayLoginDistinct(LocalDate.now().atStartOfDay()));
        return vo;
    }
}
