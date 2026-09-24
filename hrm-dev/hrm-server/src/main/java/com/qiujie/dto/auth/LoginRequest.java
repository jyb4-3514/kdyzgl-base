package com.qiujie.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求（api.md 4.1.1）。
 * <p>
 * <b>契约兼容（硬约束）</b>：原有 {@code username} / {@code password} 的名称、类型、校验与语义<b>不得变更</b>；
 * M4 新增的 {@code clientType} / {@code as} / {@code device} 一律为<b>可选</b>字段——老前端不传即与改造前行为完全一致
 * （端准入不校验、不触发设备二次验证、响应体不出现新增字段）。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 30, message = "用户名须为4-30位字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 端类型（新增，可选）：WEB=PC 网页端（仅 hrm.auth.pc-allowed-roles 内角色）；H5=移动端 */
    private String clientType;

    /** 入口视角参数（新增，可选；前端 {@code ?as=boss|staff|station}）。Java 关键字不可作字段名，JSON 键仍为 {@code as} */
    @JsonProperty("as")
    private String entryAs;

    /** 设备信息（新增，可选）：上报后服务端据 Cookie 中的设备令牌判定是否受信，未受信则触发短信二次验证 */
    private DeviceInfo device;
}
