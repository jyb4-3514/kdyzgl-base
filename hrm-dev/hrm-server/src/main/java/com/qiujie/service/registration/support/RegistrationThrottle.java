package com.qiujie.service.registration.support;

import com.qiujie.config.RegistrationProperties;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 注册「提交」频控（S-2 / M-7）：同手机号「注册提交」每日上限，与短信发码频控互补。
 * <p>
 * 为什么单列而非并入 {@code SmsCodeStore}：本计数属「建单」语义（不涉短信通道），
 * 与验证码生命周期解耦，避免把注册业务键混入 {@code hrm:sms:*} 命名空间。
 * <p>
 * 键 {@code hrm:registration:limit:phone:<phone>}（1 天 TTL，惰性重置）。
 * 边界：软限流（读后写），并发极端情况下可能略微超出，但 DB 唯一键与重复提交守卫仍为最终防线。
 */
@Component
@RequiredArgsConstructor
public class RegistrationThrottle {

    private static final String SUBMIT_LIMIT_PREFIX = "hrm:registration:limit:phone:";
    private static final long ONE_DAY_SECONDS = 86400L;

    private final StringRedisTemplate redis;
    private final RegistrationProperties properties;

    /**
     * 校验并计入一次注册提交；超限抛 400（文案不泄露内部口径）。
     *
     * @param phone 手机号（已格式校验）
     */
    public void checkSubmitAllowed(String phone) {
        int limit = properties.getSubmitDailyLimitPerPhone();
        if (limit <= 0) {
            return;
        }
        String key = SUBMIT_LIMIT_PREFIX + phone;
        if (counter(key) >= limit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "注册提交过于频繁，请稍后再试");
        }
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, Duration.ofSeconds(ONE_DAY_SECONDS));
        }
    }

    private long counter(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
