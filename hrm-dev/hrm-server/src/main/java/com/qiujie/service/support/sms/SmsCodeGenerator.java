package com.qiujie.service.support.sms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 短信验证码生成与校验（纯逻辑，可离线单测）。
 * <p>
 * 安全要点（security-auth-review §4.4）：
 * <ul>
 *   <li><b>随机性</b>：用 {@link SecureRandom} 生成，禁用 {@code Math.random()}/{@code Random}（可被预测）；</li>
 *   <li><b>恒定时间比较</b>：用 {@link MessageDigest#isEqual} 避免按字节短路造成的时序侧信道；</li>
 *   <li><b>不泄漏</b>：本类不打印任何内容，调用方亦不得将结果写入日志/异常/响应。</li>
 * </ul>
 */
public final class SmsCodeGenerator {

    /** 允许的最小验证码长度 */
    public static final int MIN_LENGTH = 4;
    /** 允许的最大验证码长度 */
    public static final int MAX_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SmsCodeGenerator() {
    }

    /**
     * 生成定长数字验证码（允许前导 0）。
     * <p>
     * 长度做防御性钳制到 [{@link #MIN_LENGTH}, {@link #MAX_LENGTH}]：
     * 过短易被暴力枚举、过长无必要且影响体验；配置层（{@code hrm.sms.code-length}）已限制范围，此处为第二道防线。
     */
    public static String generate(int length) {
        int len = Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
        char[] code = new char[len];
        for (int i = 0; i < len; i++) {
            code[i] = (char) ('0' + RANDOM.nextInt(10));
        }
        return new String(code);
    }

    /**
     * 恒定时间比较验证码。
     * <p>
     * 任一侧为 null 返回 false；长度不等时 {@link MessageDigest#isEqual} 会立即返回，仅泄漏「长度」信息
     * （长度由配置固定为明文口径，非秘密），不泄漏内容。
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
