package com.qiujie.service.auth.support;

import com.qiujie.config.AuthProperties;
import com.qiujie.service.support.sms.SmsCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 图形验证码存储（D1，可选；{@code hrm.auth.captcha-enabled=false} 时不参与登录）。
 * <p>
 * 键：{@code hrm:auth:captcha:<ticket>}（值 = 图形码；TTL = {@code hrm.auth.captcha-ttl-seconds}）。
 * 与短信验证码同口径：一次性（校验成功即删）、TTL 失效、恒定时间比较、明文绝不入日志/响应。
 * <p>
 * TODO(扩展): 当前返回固定占位图（与前端 Mock 对齐，见 {@code demo-login-redesign §6} 演示态默认不启用）；
 *   生产启用时须接入真实图形码渲染（如 kaptcha / 自绘 BufferedImage），并评估无障碍与弱网体验。
 */
@Component
@RequiredArgsConstructor
public class CaptchaStore {

    /** 图形码键前缀 */
    public static final String KEY_PREFIX = "hrm:auth:captcha:";

    /** 图形码字符个数 */
    private static final int CODE_LENGTH = 4;
    /** 图形码字符集（剔除易混字符 0/O/1/I） */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;

    /** 生成图形码并返回票号（图形码明文仅存服务端，不返回给前端） */
    public String issue() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        String ticket = "captcha." + DeviceTokenCodec.newToken();
        redis.opsForValue().set(KEY_PREFIX + ticket, code.toString(),
                Duration.ofSeconds(Math.max(1, authProperties.getCaptchaTtlSeconds())));
        return ticket;
    }

    /** 图形码是否启用（出参 requireCaptcha 的取值来源） */
    public boolean isEnabled() {
        return authProperties.isCaptchaEnabled();
    }

    /** 图形码有效期（秒，出参 expireIn） */
    public int expireInSeconds() {
        return Math.max(0, authProperties.getCaptchaTtlSeconds());
    }

    /**
     * 校验并消费图形码（一次性）。
     *
     * @return 通过返回 {@code true}（票据与图形码均已删除）；否则 {@code false}
     */
    public boolean consume(String ticket, String code) {
        if (ticket == null || ticket.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String expected = redis.opsForValue().get(KEY_PREFIX + ticket);
        if (expected == null) {
            return false;
        }
        boolean matched = SmsCodeGenerator.constantTimeEquals(expected, code.trim().toUpperCase());
        // 无论成败均删除：防止同一票据被反复试码
        redis.delete(KEY_PREFIX + ticket);
        return matched;
    }
}
