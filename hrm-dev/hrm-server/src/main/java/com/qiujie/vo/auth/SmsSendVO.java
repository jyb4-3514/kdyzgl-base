package com.qiujie.vo.auth;

import lombok.Data;

/**
 * A1 短信下发响应（对齐前端 Mock {@code smsSend} 返回体）。
 * <p>
 * <b>绝不包含验证码</b>（security-auth-review §4.4 第 ⑦ 条红线）。
 */
@Data
public class SmsSendVO {

    /** 是否已受理发送（降级通道亦为 true——短信由通道投递，服务端不判断用户是否收到） */
    private Boolean sent;

    /** 验证码有效期（秒；对齐 hrm.sms.code-ttl-seconds，Mock 固定 300） */
    private Integer expireIn;

    /** 建议的再次发送等待（秒；对齐 hrm.sms.send-interval-seconds，Mock 固定 60） */
    private Integer nextAllowedIn;

    /** 是否需要图形验证码（= hrm.auth.captcha-enabled；演示态固定 false） */
    private Boolean requireCaptcha;
}
