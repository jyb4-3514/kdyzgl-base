package com.qiujie.util;

/**
 * 敏感字段脱敏工具（决策 D5，口径对齐 Mock {@code shared/domain/mask.js}）：
 * <ul>
 *   <li>{@link #maskPhone}：11 位手机号前 3 后 4（138****5678）；非 11 位号码前 3 位 + ****；</li>
 *   <li>{@link #maskName}：姓名保留首字，其余以 * 占位（如「张**」）；</li>
 *   <li>{@link #maskBankAccount}：银行卡号保留末 4 位，其余按 4 位一组以 * 占位；</li>
 *   <li>{@link #maskIp}：IP 末段打码（192.168.1.**，对齐 Mock {@code maskIp}）。</li>
 * </ul>
 * 适用范围：所有 JSON 出参（C-07）；例外：Excel 导出保留完整手机号。
 * 查询筛选不受影响（对完整值做 LIKE）。
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /** 手机号脱敏（null/空串原样返回） */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        // 非 11 位：前 3 位 + ****（不足 3 位时取全部）
        int prefixLength = Math.min(3, phone.length());
        return phone.substring(0, prefixLength) + "****";
    }

    /**
     * 姓名脱敏（C-07，对齐 Mock {@code maskName}）：保留首字，其余用 * 占位。
     * 用于包裹收件人 / 工单相关姓名等出参。
     * 长度为 1 或空值时原样返回。
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() <= 1) {
            return name;
        }
        return String.valueOf(name.charAt(0)) + "*".repeat(name.length() - 1);
    }

    /**
     * IP 脱敏（对齐 Mock {@code maskIp}）：末段整体替换为 {@code **}（如 {@code 192.168.1.**}）。
     * 用于受信设备列表的 {@code lastIp} 出参；非 IPv4（含 IPv6 / 空值 / 无点串）<b>一律不返回原文</b>，
     * 统一返回 {@code **}（宁可少显示，不冒泄露风险）。
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == ip.length() - 1) {
            // 非 IPv4 形态（IPv6 含冒号、无点、以点结尾）：不复用前端正则口径，直接整体打码
            return "**";
        }
        return ip.substring(0, lastDot + 1) + "**";
    }

    /**
     * 银行卡号脱敏（C-07，对齐 Mock {@code maskBankAccount}）：保留末 4 位，其余数字按 4 位一组用 * 占位（组间空格）。
     * 长度 ≤4 时全部以 * 占位（不暴露任何真实位）。
     * 为什么先剔除空白：卡号常以空格分组书写，脱敏需按纯数字位数计算。
     */
    public static String maskBankAccount(String account) {
        if (account == null || account.isEmpty()) {
            return account;
        }
        String digits = account.replaceAll("\\s", "");
        if (digits.length() <= 4) {
            return "*".repeat(digits.length());
        }
        String masked = "*".repeat(digits.length() - 4);
        StringBuilder head = new StringBuilder();
        for (int start = 0; start < masked.length(); start += 4) {
            if (start > 0) {
                head.append(' ');
            }
            head.append(masked, start, Math.min(start + 4, masked.length()));
        }
        return head + " " + digits.substring(digits.length() - 4);
    }
}
