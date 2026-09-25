package com.qiujie.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求（api.md 4.1.1）。
 * <p>
 * <b>契约兼容（硬约束）</b>：原有 {@code username} / {@code password} 的名称、类型、校验与语义<b>不得变更</b>；
 * M4 新增的 {@code clientType} / {@code as} / {@code device} 一律为<b>可选</b>字段。
 * <p>
 * <b>端准入（必读，fail-closed）</b>：端类型优先取请求头 {@code X-Client-Type}（新契约，取值 {@code ADMIN/BOSS/STAFF}），
 * 其次取本字段 {@code clientType}（兼容旧前端；旧值 {@code H5} 按 {@code as} 派生为 {@code BOSS/STAFF}）。
 * <b>缺省 / 未知 / 非法端一律拒登（1110）</b>——不再「不传即不校验」，老前端须显式上报端类型。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 30, message = "用户名须为4-30位字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 端类型（可选）：{@code ADMIN}=PC 管理端 / {@code BOSS}=管理端 H5 / {@code STAFF}=员工端 H5；
     * 旧前端仍可上报 {@code WEB}（按 PC 口径）或 {@code H5}（按 {@code as} 派生端）。
     * 请求头 {@code X-Client-Type} 存在时以其为准；<b>缺省 / 未知 / 非法即 1110</b>。
     */
    private String clientType;

    /** 入口视角参数（新增，可选；前端 {@code ?as=boss|staff|station}）。Java 关键字不可作字段名，JSON 键仍为 {@code as} */
    @JsonProperty("as")
    private String entryAs;

    /** 设备信息（新增，可选）：上报后服务端据 Cookie 中的设备令牌判定是否受信，未受信则触发短信二次验证 */
    private DeviceInfo device;
}
