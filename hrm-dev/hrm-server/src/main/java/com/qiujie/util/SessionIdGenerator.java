package com.qiujie.util;

import java.security.SecureRandom;

/**
 * 会话标识（sid）生成器（ADR-MC-03 会话多端化地基）。
 * <p>
 * <b>为什么不用 {@code UUID.randomUUID()}</b>：UUID v4 虽由 SecureRandom 驱动，但固定消耗版本/变体位，
 * 有效随机位仅 122 bit，且长度与格式固定（含连字符），熵预算不可调。本类直接取
 * {@link SecureRandom} 的 <b>32 字节（256 bit）</b> 转 64 位十六进制串，熵更高、
 * <b>不承载任何可推导信息</b>（不含时间戳/员工号/序号），满足「会话 key 不得可枚举」的安全要求。
 * <p>
 * 生成值同时用作 JWT {@code jti} 与 Redis 会话主键 {@code hrm:session:{sid}}。
 * <p>
 * 新 token 的 {@code jti} 恒为 64 位小写十六进制；历史 token 的 {@code jti} 为 UUID（36 字符），
 * 二者格式天然可区分，配合 {@link #isValid(String)} 可静态识别「旧 token 回退分支」。
 */
public final class SessionIdGenerator {

    /** 随机字节数（256 bit）；十六进制串长度 = 2 × 本值 */
    public static final int BYTE_LENGTH = 32;
    /** sid 十六进制串长度（固定 64） */
    public static final int SID_LENGTH = BYTE_LENGTH * 2;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SessionIdGenerator() {
    }

    /**
     * 生成高熵会话标识（64 位小写十六进制）。
     * <p>
     * {@link SecureRandom} 线程安全且首次调用会完成熵池自播种；此处为静态单例，不新建实例（避免重复播种开销）。
     */
    public static String newSid() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /** 是否为本生成器产出的合法 sid（长度与字符集校验）；用于避免把任意字符串当作 Redis key 使用 */
    public static boolean isValid(String sid) {
        if (sid == null || sid.length() != SID_LENGTH) {
            return false;
        }
        for (int i = 0; i < sid.length(); i++) {
            char c = sid.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** 字节数组 → 小写十六进制串 */
    static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
