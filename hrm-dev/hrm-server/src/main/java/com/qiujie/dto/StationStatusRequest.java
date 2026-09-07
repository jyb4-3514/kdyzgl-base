package com.qiujie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 驿站启用/停用请求（api.md 4.5.4）。
 */
@Data
public class StationStatusRequest {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值仅支持 0=停用/1=启用")
    @Max(value = 1, message = "状态取值仅支持 0=停用/1=启用")
    private Integer status;
}
