package com.qiujie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑部门请求（api.md 4.4.3）：
 * 仅允许修改名称与排序；不允许修改父级（决策 D12），传入 parentId 即拒绝（400）。
 */
@Data
public class DepartmentUpdateRequest {

    /** 父部门不允许修改：字段出现（非 null）时接口直接拒绝 */
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    @Size(min = 1, max = 50, message = "部门名称须为1-50个字符")
    private String deptName;

    private Integer sortOrder;
}
