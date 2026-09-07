package com.qiujie.util;

import com.qiujie.common.SessionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 登录会话封装（决策 D3）：
 * - Key：hrm:session:{employeeId}（前缀 hrm: 隔离同实例其他应用）；
 * - Value：SessionInfo（含 jti，比对 jti 实现互踢/强制下线）；
 * - TTL：与 JWT 有效期一致（读配置 jwt.expire，默认 86400 秒）。
 */
@Component
public class SessionUtil {

    public static final String KEY_PREFIX = "hrm:session:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlSeconds;

    public SessionUtil(RedisTemplate<String, Object> redisTemplate,
                       @Value("${jwt.expire:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    /** 建立会话（同账号再次登录覆盖旧值，即互踢） */
    public void save(Long employeeId, SessionInfo info) {
        redisTemplate.opsForValue().set(KEY_PREFIX + employeeId, info, Duration.ofSeconds(ttlSeconds));
    }

    /** 读取会话；不存在或类型异常返回 null */
    public SessionInfo get(Long employeeId) {
        Object value = redisTemplate.opsForValue().get(KEY_PREFIX + employeeId);
        return value instanceof SessionInfo sessionInfo ? sessionInfo : null;
    }

    /** 删除会话（登出/禁用/删除/重置密码时调用，幂等） */
    public void delete(Long employeeId) {
        redisTemplate.delete(KEY_PREFIX + employeeId);
    }
}
