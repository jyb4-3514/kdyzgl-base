package com.qiujie.dto.employee;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工查询条件（api.md 4.3.1 / 4.3.10，查询参数绑定）：
 * 继承 {@link StationScopedQuery} 获得 {@code pageNum}/{@code pageSize}（含 C-04 越界校验）
 * 与 {@code stationId}（非 ADMIN 由 L1 拦截器强制收敛为本人驿站）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeQuery extends StationScopedQuery {

    /** 姓名 / 登录账号 / 手机号 三字段模糊匹配（任一命中） */
    private String keyword;

    /** 部门筛选，含其全部子部门（后端内存递归展开） */
    private Long deptId;

    /** 0=禁用，1=启用 */
    private Integer status;
}
