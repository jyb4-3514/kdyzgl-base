package com.qiujie.dto;

import lombok.Data;

/**
 * 员工查询条件（api.md 4.3.1 / 4.3.10，查询参数绑定，无需 Bean 校验）：
 * pageNum 默认 1；pageSize 默认 10、上限 100（Service 层兜底钳制）。
 */
@Data
public class EmployeeQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /** 姓名 / 登录账号 / 手机号 三字段模糊匹配（任一命中） */
    private String keyword;

    /** 部门筛选，含其全部子部门（后端内存递归展开） */
    private Long deptId;

    private Long stationId;

    /** 0=禁用，1=启用 */
    private Integer status;
}
