package com.qiujie.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取：
 * 生产环境 Nginx 反代透传 X-Forwarded-For / X-Real-IP（部署依赖，见 docs/deploy.md），
 * 取 X-Forwarded-For 首个非空值，其次 X-Real-IP，最后回退 remoteAddr。
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            String ip = (commaIndex > 0 ? forwarded.substring(0, commaIndex) : forwarded).trim();
            if (!ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !UNKNOWN.equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
