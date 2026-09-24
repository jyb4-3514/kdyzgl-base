package com.qiujie.dto.auth;

import lombok.Data;

/**
 * B2 新设备短信二次验证请求（{@code POST /api/v1/auth/device/verify}，公开）。
 * <p>
 * 入参对齐前端 Mock：{@code twoFactorTicket} + {@code code}（前端不传手机号——设备步手机号为脱敏只读）。
 */
@Data
public class DeviceVerifyRequest {

    /** 二次验证票据（由 {@code /auth/login} 新设备分支下发，短 TTL，一次性） */
    private String twoFactorTicket;

    /** 短信验证码（DEVICE_VERIFY 场景，按员工暂存） */
    private String code;
}
