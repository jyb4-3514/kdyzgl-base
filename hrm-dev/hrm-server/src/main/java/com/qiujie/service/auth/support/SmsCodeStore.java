package com.qiujie.service.auth.support;

import com.qiujie.config.SmsProperties;
import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsThrottlePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 短信验证码与发送频控的 Redis 存储（M4）。
 * <p>
 * <b>键规范</b>（架构 §4.4.2 / §4.4.3；统一 hrm:sms: 前缀）：
 * <ul>
 *   <li>验证码：hrm:sms:code:&lt;scene&gt;:&lt;identifier&gt;（String；TTL = hrm.sms.code-ttl-seconds）；</li>
 *   <li>失败计数：hrm:sms:attempt:&lt;scene&gt;:&lt;identifier&gt;（Integer；TTL 同验证码；校验成功即随码删除）；</li>
 *   <li>最近发送时刻：hrm:sms:last:phone:&lt;phone&gt;（epoch 秒；TTL = 最小间隔）；</li>
 *   <li>限频计数：hrm:sms:limit:phone|ip|device|account:&lt;key&gt;（INCR + EXPIRE）。</li>
 * </ul>
 * 其中 identifier 在 LOGIN 场景为手机号、在 DEVICE_VERIFY 场景为<b>员工 id</b>
 * （设备步的手机号在页面上是脱敏只读的，前端不接触明文，故以员工定位，与前端 Mock 口径一致）。
 * <p>
 * <b>红线</b>：本类不打印任何内容；验证码明文只在「服务端 → 短信通道」一次流转，
 * <b>绝不进日志 / 异常 / 响应体</b>（security-auth-review §4.4 第 ⑦ 条）。
 * 为什么用 {@link StringRedisTemplate}：验证码与计数走原生字符串 / {@code INCR}，
 * 与全局 JSON 序列化模板（{@code RedisTemplate<String,Object>}）隔离，避免类型信息污染数值。
 */
@Component
@RequiredArgsConstructor
public class SmsCodeStore {

    private static final String CODE_PREFIX = "hrm:sms:code:";
    private static final String ATTEMPT_PREFIX = "hrm:sms:attempt:";
    private static final String LAST_SEND_PREFIX = "hrm:sms:last:phone:";
    private static final String LIMIT_PHONE_PREFIX = "hrm:sms:limit:phone:";
    private static final String LIMIT_IP_PREFIX = "hrm:sms:limit:ip:";
    private static final String LIMIT_DEVICE_PREFIX = "hrm:sms:limit:device:";
    private static final String LIMIT_ACCOUNT_PREFIX = "hrm:sms:limit:account:";
    /** 全局日上限计数器（M-7/S-2 成本兜底；单键计数，1 天 TTL 惰性重置） */
    private static final String LIMIT_GLOBAL_KEY = "hrm:sms:limit:global";

    private static final long ONE_HOUR_SECONDS = 3600L;
    private static final long ONE_DAY_SECONDS = 86400L;

    private final StringRedisTemplate redis;
    private final SmsProperties smsProperties;

    // ==================== 验证码 ====================

    /** 写入验证码并清空旧失败计数（重新发码即重置尝试次数） */
    public void saveCode(SmsScene scene, String identifier, String code) {
        Duration ttl = Duration.ofSeconds(Math.max(1, smsProperties.getCodeTtlSeconds()));
        redis.opsForValue().set(codeKey(scene, identifier), code, ttl);
        redis.delete(attemptKey(scene, identifier));
    }

    /** 读取验证码（不存在 / 已过期返回 null） */
    public String getCode(SmsScene scene, String identifier) {
        return redis.opsForValue().get(codeKey(scene, identifier));
    }

    /** 删除验证码与失败计数（校验成功一次性作废 / 达尝试上限作废） */
    public void clearCode(SmsScene scene, String identifier) {
        redis.delete(List.of(codeKey(scene, identifier), attemptKey(scene, identifier)));
    }

    /** 当前已失败次数（无记录返回 0） */
    public int attempts(SmsScene scene, String identifier) {
        return parseInt(redis.opsForValue().get(attemptKey(scene, identifier)));
    }

    /** 失败次数 +1（TTL 与验证码一致，避免计数残留） */
    public int incrementAttempts(SmsScene scene, String identifier) {
        String key = attemptKey(scene, identifier);
        Long value = redis.opsForValue().increment(key);
        long count = value == null ? 0L : value;
        if (count == 1L) {
            redis.expire(key, Duration.ofSeconds(Math.max(1, smsProperties.getCodeTtlSeconds())));
        }
        return (int) count;
    }

    // ==================== 频控（全维） ====================

    /**
     * 是否应拦截本次发送（任一维度命中即拦截 → 1101）。
     * <p>
     * 判定顺序：同手机号最小间隔 → 同手机号每日上限 → 同 IP 每小时 → 同设备每小时 → 同账号每日 → 全局每日。
     * <b>读取不写入</b>：命中拦截时不消耗计数（与前端 Mock「仅成功发送才更新 lastSend」一致），
     * 通过后由 {@link #recordSend} 统一入账。
     */
    public boolean isSendBlocked(String phone, String ip, String deviceId, Long employeeId, long nowEpochSec) {
        if (SmsThrottlePolicy.withinSendInterval(lastSendEpochSeconds(phone), nowEpochSec,
                smsProperties.getSendIntervalSeconds())) {
            return true;
        }
        if (SmsThrottlePolicy.dailyLimitExceeded((int) counter(LIMIT_PHONE_PREFIX + phone),
                smsProperties.getDailyLimitPerPhone())) {
            return true;
        }
        if (ip != null && !ip.isBlank()
                && SmsThrottlePolicy.dailyLimitExceeded((int) counter(LIMIT_IP_PREFIX + ip),
                smsProperties.getIpHourlyLimit())) {
            return true;
        }
        if (deviceId != null && !deviceId.isBlank()
                && SmsThrottlePolicy.dailyLimitExceeded((int) counter(LIMIT_DEVICE_PREFIX + deviceId),
                smsProperties.getDeviceHourlyLimit())) {
            return true;
        }
        if (employeeId != null
                && SmsThrottlePolicy.dailyLimitExceeded((int) counter(LIMIT_ACCOUNT_PREFIX + employeeId),
                smsProperties.getAccountDailyLimit())) {
            return true;
        }
        // 全局日上限兜底：不区分手机号/账号，阻断「持续换号」把成本放大到不可控
        return SmsThrottlePolicy.dailyLimitExceeded((int) counter(LIMIT_GLOBAL_KEY),
                smsProperties.getGlobalDailyLimit());
    }

    /** 记一次成功发送：刷新「最近发送时刻」并按各维度累计计数（TTL：手机号/账号 1 天，IP/设备 1 小时） */
    public void recordSend(String phone, String ip, String deviceId, Long employeeId, long nowEpochSec) {
        redis.opsForValue().set(LAST_SEND_PREFIX + phone, String.valueOf(nowEpochSec),
                Duration.ofSeconds(Math.max(1, smsProperties.getSendIntervalSeconds())));
        incrementWithTtl(LIMIT_PHONE_PREFIX + phone, ONE_DAY_SECONDS);
        if (employeeId != null) {
            // 无归属账号（理论上不会发生）不累计账号维度，避免产生 "account:null" 共享计数器
            incrementWithTtl(LIMIT_ACCOUNT_PREFIX + employeeId, ONE_DAY_SECONDS);
        }
        if (ip != null && !ip.isBlank()) {
            incrementWithTtl(LIMIT_IP_PREFIX + ip, ONE_HOUR_SECONDS);
        }
        if (deviceId != null && !deviceId.isBlank()) {
            incrementWithTtl(LIMIT_DEVICE_PREFIX + deviceId, ONE_HOUR_SECONDS);
        }
        // 全局计量与手机号同 TTL（1 天），保证「读」与「写」的窗口口径一致
        incrementWithTtl(LIMIT_GLOBAL_KEY, ONE_DAY_SECONDS);
    }

    /** 建议的重发等待秒数（前端倒计时对齐用；取同手机号最小间隔） */
    public int nextAllowedInSeconds() {
        return Math.max(0, smsProperties.getSendIntervalSeconds());
    }

    /** 验证码有效期（秒，出参 expireIn） */
    public int codeExpireInSeconds() {
        return Math.max(0, smsProperties.getCodeTtlSeconds());
    }

    // ==================== 内部 ====================

    private Long lastSendEpochSeconds(String phone) {
        String value = redis.opsForValue().get(LAST_SEND_PREFIX + phone);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long counter(String key) {
        return parseInt(redis.opsForValue().get(key));
    }

    private void incrementWithTtl(String key, long ttlSeconds) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
    }

    private String codeKey(SmsScene scene, String identifier) {
        return CODE_PREFIX + scene.name() + ":" + identifier;
    }

    private String attemptKey(SmsScene scene, String identifier) {
        return ATTEMPT_PREFIX + scene.name() + ":" + identifier;
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
