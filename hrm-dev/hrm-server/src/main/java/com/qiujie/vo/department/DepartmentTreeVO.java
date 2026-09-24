package com.qiujie.vo.department;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点（api.md 4.4.1）。employeeCount 为直属员工数（不含子部门）。
 */
@Data
public class DepartmentTreeVO {

    private Long id;

    /** 0=根节点 */
    private Long parentId;
    private String deptName;
    private Integer sortOrder;

    /** 直属员工数（含禁用，不含已删除） */
    private Long employeeCount;

    private List<DepartmentTreeVO> children = new ArrayList<>();
}
