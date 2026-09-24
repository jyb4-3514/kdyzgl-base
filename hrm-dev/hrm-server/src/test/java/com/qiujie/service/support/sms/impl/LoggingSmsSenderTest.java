package com.qiujie.service.support.sms.impl;

import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsSendResult;
import com.qiujie.util.DesensitizeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短信降级实现单测（M2）：脱敏手机号、只输出位数、<b>日志绝不含验证码明文</b>。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class LoggingSmsSenderTest {

    private static final String PHONE = "13812345678";
    private static final String CODE = "654321";

    @Test
    @DisplayName("降级发送返回成功且通道标识为 logging（不真正出网）")
    void sendReturnsSuccess() {
        SmsSendResult result = new LoggingSmsSender().send(PHONE, SmsScene.LOGIN, CODE);
        assertTrue(result.success());
        assertEquals(LoggingSmsSender.PROVIDER, result.provider());
    }

    @Test
    @DisplayName("日志文案：含脱敏手机号与位数、场景名；不含手机号明文、不含验证码明文")
    void logMessageRedLines() {
        String message = LoggingSmsSender.buildLogMessage(DesensitizeUtil.maskPhone(PHONE), SmsScene.LOGIN, CODE.length());
        assertTrue(message.contains("138****5678"), "应含脱敏手机号");
        assertTrue(message.contains("6 位"), "应含验证码位数");
        assertTrue(message.contains("LOGIN"), "应含场景名");
        assertFalse(message.contains(PHONE), "不得出现手机号明文");
        assertFalse(message.contains(CODE), "不得出现验证码明文");
    }

    @Test
    @DisplayName("日志文案边界：scene 为 null 归一 UNKNOWN；code 为 null 位数为 0")
    void logMessageBoundaries() {
        String message = LoggingSmsSender.buildLogMessage("138****5678", null, 0);
        assertTrue(message.contains("UNKNOWN"));
        assertTrue(message.contains("0 位"));
    }
}
