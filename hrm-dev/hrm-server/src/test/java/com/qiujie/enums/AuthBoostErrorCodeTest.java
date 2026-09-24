package com.qiujie.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 认证增强码 11xx 单测（M4）：码值与文案逐条对齐 Mock {@code constants/errorCode.js#CODE_MESSAGE}。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AuthBoostErrorCodeTest {

    @Test
    @DisplayName("11xx 码值：1101–1110 逐条")
    void codes() {
        assertEquals(1101, ErrorCode.SMS_RATE_LIMITED.getCode());
        assertEquals(1102, ErrorCode.SMS_CODE_INVALID.getCode());
        assertEquals(1103, ErrorCode.SMS_CODE_ATTEMPTS_EXCEEDED.getCode());
        assertEquals(1104, ErrorCode.DEVICE_NEED_VERIFY.getCode());
        assertEquals(1105, ErrorCode.SMS_UNAVAILABLE.getCode());
        assertEquals(1106, ErrorCode.CAPTCHA_INVALID.getCode());
        assertEquals(1107, ErrorCode.DEVICE_REVOKED.getCode());
        assertEquals(1108, ErrorCode.SESSION_EXPIRED.getCode());
        assertEquals(1109, ErrorCode.PHONE_NOT_BOUND.getCode());
        assertEquals(1110, ErrorCode.LOGIN_CLIENT_NOT_ALLOWED.getCode());
    }

    @Test
    @DisplayName("11xx 文案：与前端 CODE_MESSAGE 完全一致（逐字）")
    void messages() {
        assertEquals("验证码发送过于频繁，请稍后再试", ErrorCode.SMS_RATE_LIMITED.getMessage());
        assertEquals("验证码错误或已过期，请重新获取", ErrorCode.SMS_CODE_INVALID.getMessage());
        assertEquals("验证码尝试次数过多，请重新获取", ErrorCode.SMS_CODE_ATTEMPTS_EXCEEDED.getMessage());
        assertEquals("检测到新设备，需短信验证", ErrorCode.DEVICE_NEED_VERIFY.getMessage());
        assertEquals("短信服务暂不可用，请稍后重试", ErrorCode.SMS_UNAVAILABLE.getMessage());
        assertEquals("图形验证码错误或已失效，请重新输入", ErrorCode.CAPTCHA_INVALID.getMessage());
        assertEquals("该设备已被撤销，请重新登录", ErrorCode.DEVICE_REVOKED.getMessage());
        assertEquals("登录已到期，请重新登录", ErrorCode.SESSION_EXPIRED.getMessage());
        assertEquals("该账号未绑定手机号，无法短信验证", ErrorCode.PHONE_NOT_BOUND.getMessage());
        assertEquals("该账号无权登录此端", ErrorCode.LOGIN_CLIENT_NOT_ALLOWED.getMessage());
    }
}
