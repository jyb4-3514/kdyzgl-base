package com.qiujie.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A2 短信验证码登录请求（{@code POST /api/v1/auth/sms/login}，公开）。
 * <p>
 * 入参对齐前端 Mock：{@code phone} + {@code code} + {@code clientType} + {@code as} + {@code device}。
 * 短信本身即第二因子，登录即视为受信（不再触发设备二次验证）。
 */
@Data
public class SmsLoginRequest {

    /** 手机号（必填；格式由 Service 校验，错误码 400） */
    private String phone;

    /** 短信验证码（必填） */
    private String code;

    /**
     * 端类型：{@code ADMIN}=PC 管理端 / {@code BOSS}=管理端 H5 / {@code STAFF}=员工端 H5；
     * 旧前端仍可上报 {@code WEB}（按 PC 口径）或 {@code H5}（按 {@code as} 派生端）。
     * <b>缺省 / 未知 / 非法即 1110</b>（fail-closed；短信登录不携带 {@code X-Client-Type} 头）。
     */
    private String clientType;

    /** 入口视角参数（前端 {@code ?as=boss|staff|station}）。Java 关键字不可作字段名，JSON 键仍为 {@code as} */
    @JsonProperty("as")
    private String entryAs;

    /** 设备信息（弱信号；用于设备指纹与受信登记） */
    private DeviceInfo device;
}
