package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Department;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 部门 Mapper。
 */
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 各部门直属员工数（含禁用员工、不含已删除，供部门树 employeeCount 使用）。
     * 注：@Select 原生 SQL 不经过 MyBatis-Plus 逻辑删除拦截，需显式带 is_deleted = 0。
     * 列别名保持小写下划线，MySQL / PostgreSQL 行为一致。
     */
    @Select("SELECT dept_id, COUNT(*) AS cnt FROM employee "
            + "WHERE is_deleted = 0 AND dept_id IS NOT NULL GROUP BY dept_id")
    List<Map<String, Object>> countEmployeesByDept();
}
