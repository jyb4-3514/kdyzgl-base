package com.qiujie.service.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 设备信任令牌编解码（纯逻辑，无外部依赖，可离线单测）。
 * <p>
 * <b>安全要求</b>（security-auth-review §4.2 加固建议 ①）：设备信任态<b>必须由服务端持有</b>——
 * <ul>
 *   <li>令牌由服务端以 {@link SecureRandom} 生成 256 bit 高熵串（前端不生成、不可预测）；</li>
 *   <li>库中<b>只存 SHA-256 摘要</b>（{@code auth_trusted_device.device_token_hash}），<b>明文仅在下发时出现一次</b>
 *       （经 HttpOnly + Secure Cookie 下发，前端 JS 不可读）；</li>
 *   <li>本类不打印任何内容；调用方亦不得把明文写入日志 / 异常 / 响应体。</li>
 * </ul>
 * 为什么不用 JWT 承载信任：设备令牌与主会话解绑（改密/强制下线时须能独立失效），且需可逐一撤销与限数。
 */
public final class DeviceTokenCodec {

    /** 令牌字节数：32 字节 = 256 bit，hex 后 64 字符（与 {@code CHAR(64)} 摘要列无关，明文不落库） */
    private static final int TOKEN_BYTES = 32;

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceTokenCodec() {
    }

    /** 生成新的设备令牌明文（仅此一次出现；调用方须立即下发并丢弃引用） */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /**
     * 计算设备令牌摘要（SHA-256 hex，入库比对用）。
     *
     * @param token 令牌明文；{@code null}/空白返回 {@code null}（表示「无令牌可校验」，绝不匹配任何行）
     */
    public static String sha256Hex(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return toHex(digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    /** 恒定时间比较两个摘要（防时序侧信道）；任一侧为 null 返回 false */
    public static boolean digestEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必备算法缺失属环境致命问题，直接失败而非静默降级（绝不退化为弱摘要）
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hex[i * 2] = HEX[value >>> 4];
            hex[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(hex);
    }
}
