package com.qiujie.service.auth.support;

import com.qiujie.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 设备信任令牌 Cookie 读写（security-auth-review §4.2 加固建议 ①）。
 * <p>
 * <b>为什么用 Cookie 而非响应体/前端存储</b>：
 * <ul>
 *   <li>{@code HttpOnly} → 前端 JS <b>读不到</b>信任令牌，杜绝「前端持久化信任标志被复制伪造」的绕过路径；</li>
 *   <li>{@code Secure} → 仅在 HTTPS 下回传（是否 HTTPS 由 {@code X-Forwarded-Proto} 或 {@code request.isSecure()} 判定，
 *       兼容 Nginx 反代）；</li>
 *   <li>{@code SameSite=Lax} + 限定 {@code Path=/api/v1/auth} → 缩小附带范围，缓解 CSRF 与令牌扩大暴露；</li>
 *   <li>令牌明文<b>仅在下发时出现一次</b>，库中只存 SHA-256 摘要（见 {@link DeviceTokenCodec}）。</li>
 * </ul>
 * 说明：本站前后端同源（Nginx 反代 / Vite proxy，决策 D9），同源请求默认携带 Cookie，前端无需改代码。
 */
@Component
@RequiredArgsConstructor
public class DeviceCookieSupport {

    /** Cookie 名（全 ASCII；与设备信任令牌语义对应） */
    public static final String COOKIE_NAME = "hrm_device_token";
    /** 仅随认证端点回传，缩小暴露面 */
    public static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthProperties authProperties;

    /** 读取请求携带的设备令牌（无返回 null） */
    public String read(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    /** 下发/刷新设备信任令牌（Max-Age 为设备信任有效期） */
    public void write(HttpServletResponse response, HttpServletRequest request, String deviceToken) {
        if (response == null || deviceToken == null || deviceToken.isBlank()) {
            return;
        }
        ResponseCookie cookie = baseBuilder(deviceToken, request)
                .maxAge(Duration.ofSeconds(Math.max(1, authProperties.getDeviceTrustTtlSeconds())))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 清除设备令牌（撤销当前设备 / 强制下线时使用；Max-Age=0 即删除） */
    public void clear(HttpServletResponse response, HttpServletRequest request) {
        if (response == null) {
            return;
        }
        ResponseCookie cookie = baseBuilder("", request).maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 请求是否走 HTTPS（Nginx 反代透传 X-Forwarded-Proto；直连时看 request.isSecure()） */
    public static boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            int comma = forwardedProto.indexOf(',');
            String proto = (comma > 0 ? forwardedProto.substring(0, comma) : forwardedProto).trim();
            return "https".equalsIgnoreCase(proto);
        }
        return request.isSecure();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path(COOKIE_PATH);
    }
}
