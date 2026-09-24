package com.qiujie.dto.employee;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工启用/禁用请求（api.md 4.3.6）。
 */
@Data
public class EmployeeStatusRequest {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值仅支持 0=禁用/1=启用")
    @Max(value = 1, message = "状态取值仅支持 0=禁用/1=启用")
    private Integer status;
}
