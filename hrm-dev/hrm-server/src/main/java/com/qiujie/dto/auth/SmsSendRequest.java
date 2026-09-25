package com.qiujie.dto.auth;

import lombok.Data;

/**
 * A1 短信验证码下发请求（{@code POST /api/v1/auth/sms/send}，公开）。
 * <p>
 * 校验口径与前端 Mock 一致（按场景分流，故不用注解做「一刀切必填」）：
 * <ul>
 *   <li>{@code LOGIN}：{@code phone} 必填且为 11 位手机号；</li>
 *   <li>{@code DEVICE_VERIFY}：{@code twoFactorTicket} 必填（设备步手机号在页面侧脱敏只读，
 *       前端不接触明文，故以票据定位员工）；<b>手机号非必填</b>；</li>
 *   <li>{@code captcha-enabled=true} 时须附有效 {@code captchaTicket} + {@code captchaCode}，否则 1106。</li>
 * </ul>
 */
@Data
public class SmsSendRequest {

    /** 场景：LOGIN / DEVICE_VERIFY / PERIODIC_REAUTH（缺省按 LOGIN） */
    private String scene;

    /** 手机号（LOGIN 场景必填） */
    private String phone;

    /** 二次验证票据（DEVICE_VERIFY 场景必填；由 {@code /auth/login} 新设备分支下发） */
    private String twoFactorTicket;

    /** 设备标识（弱信号；参与同设备维度限频与审计） */
    private String deviceId;

    /** 图形验证码票据（captcha 开启时必填） */
    private String captchaTicket;

    /** 图形验证码（captcha 开启时必填） */
    private String captchaCode;

    /** 端类型（{@code ADMIN}/{@code BOSS}/{@code STAFF}；各场景仅作设备/审计维度，DEVICE_VERIFY 场景的端准入以票据内的端类型为准） */
    private String clientType;
}
