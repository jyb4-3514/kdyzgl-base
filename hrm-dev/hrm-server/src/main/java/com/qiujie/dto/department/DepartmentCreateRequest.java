package com.qiujie.dto.department;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增部门请求（api.md 4.4.2）。
 */
@Data
public class DepartmentCreateRequest {

    /** 0=根节点；否则须为存在且未删除的部门 */
    @NotNull(message = "父部门不能为空（0=根部门）")
    @Min(value = 0, message = "父部门取值不正确（0=根部门）")
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    @Size(min = 1, max = 50, message = "部门名称须为1-50个字符")
    private String deptName;

    /** 同级排序（升序），默认 0 */
    private Integer sortOrder;
}
