package com.qiujie.service.auth.support;

import com.qiujie.config.SmsProperties;
import com.qiujie.service.support.sms.SmsScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码与全维频控 Redis 存储单测（M4，Mockito 模拟 StringRedisTemplate，无需真实 Redis）：
 * 验证码 TTL/一次性、失败计数 TTL、同号间隔、同号/同 IP/同账号限频。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SmsCodeStoreTest {

    private static final String PHONE = "13800000000";
    private static final String CODE_KEY = "hrm:sms:code:LOGIN:" + PHONE;
    private static final String ATTEMPT_KEY = "hrm:sms:attempt:LOGIN:" + PHONE;
    private static final String LAST_KEY = "hrm:sms:last:phone:" + PHONE;
    private static final String PHONE_LIMIT_KEY = "hrm:sms:limit:phone:" + PHONE;
    private static final String IP_LIMIT_KEY = "hrm:sms:limit:ip:1.2.3.4";
    private static final String ACCOUNT_LIMIT_KEY = "hrm:sms:limit:account:7";

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private SmsCodeStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // 默认值与 application.yml 一致：TTL 300 / 间隔 60 / 同号日限 10 / IP 时限 20 / 设备时限 10 / 账号日限 10
        store = new SmsCodeStore(redis, new SmsProperties());
    }

    @Test
    @DisplayName("saveCode：写验证码（TTL=300s）并清空旧失败计数")
    void saveCodeSetsTtlAndClearsAttempts() {
        store.saveCode(SmsScene.LOGIN, PHONE, "123456");

        verify(valueOps).set(eq(CODE_KEY), eq("123456"), eq(Duration.ofSeconds(300)));
        // saveCode 只清旧失败计数；验证码由上一行 set 覆写，若连 CODE_KEY 一起删会把刚写入的码删掉（与 db.md §短信验证码口径矛盾）
        verify(redis).delete(ATTEMPT_KEY);
    }

    @Test
    @DisplayName("getCode：读到即返回；不存在返回 null")
    void getCode() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        org.junit.jupiter.api.Assertions.assertEquals("123456", store.getCode(SmsScene.LOGIN, PHONE));

        when(valueOps.get(CODE_KEY)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertNull(store.getCode(SmsScene.LOGIN, PHONE));
    }

    @Test
    @DisplayName("incrementAttempts：首次（=1）时补设 TTL=300s，避免计数永久残留")
    void incrementAttemptsSetsTtlOnFirstIncrement() {
        when(valueOps.increment(ATTEMPT_KEY)).thenReturn(1L);
        org.junit.jupiter.api.Assertions.assertEquals(1, store.incrementAttempts(SmsScene.LOGIN, PHONE));
        verify(redis).expire(eq(ATTEMPT_KEY), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("频控-同号间隔：距上次发送 10s（<60s）→ 拦截；距上次 90s → 放行")
    void phoneIntervalThrottle() {
        long now = 1_700_000_000L;
        when(valueOps.get(LAST_KEY)).thenReturn(String.valueOf(now - 10));
        assertTrue(store.isSendBlocked(PHONE, null, null, 7L, now));

        when(valueOps.get(LAST_KEY)).thenReturn(String.valueOf(now - 90));
        assertFalse(store.isSendBlocked(PHONE, null, null, 7L, now));
    }

    @Test
    @DisplayName("频控-同号每日上限：计数达 10 → 拦截")
    void phoneDailyLimitThrottle() {
        long now = 1_700_000_000L;
        when(valueOps.get(PHONE_LIMIT_KEY)).thenReturn("10");
        assertTrue(store.isSendBlocked(PHONE, null, null, null, now));

        when(valueOps.get(PHONE_LIMIT_KEY)).thenReturn("9");
        assertFalse(store.isSendBlocked(PHONE, null, null, null, now));
    }

    @Test
    @DisplayName("频控-同 IP / 同账号维度：达上限即拦截（阻断换号/换设备绕过）")
    void ipAndAccountThrottle() {
        long now = 1_700_000_000L;
        when(valueOps.get(IP_LIMIT_KEY)).thenReturn("20");
        assertTrue(store.isSendBlocked(PHONE, "1.2.3.4", null, null, now));

        when(valueOps.get(IP_LIMIT_KEY)).thenReturn("0");
        when(valueOps.get(ACCOUNT_LIMIT_KEY)).thenReturn("10");
        assertTrue(store.isSendBlocked(PHONE, "1.2.3.4", null, 7L, now));
    }

    @Test
    @DisplayName("recordSend：刷新最近发送时刻（TTL=间隔）并按维度累计计数")
    void recordSendUpdatesLastAndCounters() {
        long now = 1_700_000_000L;
        store.recordSend(PHONE, "1.2.3.4", "dev-1", 7L, now);

        verify(valueOps).set(eq(LAST_KEY), eq(String.valueOf(now)), eq(Duration.ofSeconds(60)));
        verify(valueOps).increment(PHONE_LIMIT_KEY);
        verify(valueOps).increment(ACCOUNT_LIMIT_KEY);
        verify(valueOps).increment("hrm:sms:limit:ip:1.2.3.4");
        verify(valueOps).increment("hrm:sms:limit:device:dev-1");
    }

    @Test
    @DisplayName("recordSend：ip/设备/账号为空时不计该维度（不产生 ip:''/device:null/account:null 之类共享键）")
    void recordSendSkipsBlankDimensions() {
        store.recordSend(PHONE, "  ", null, null, 1_700_000_000L);

        // 仅同手机号维度必计数；其余维度一律不得产生键
        verify(valueOps).increment(PHONE_LIMIT_KEY);
        verify(valueOps, never()).increment(contains(":limit:ip:"));
        verify(valueOps, never()).increment(contains(":limit:device:"));
        verify(valueOps, never()).increment(contains(":limit:account:"));
        verify(valueOps).set(eq(LAST_KEY), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("出参口径：expireIn=300、nextAllowedIn=60（与前端 Mock 一致）")
    void outputSeconds() {
        org.junit.jupiter.api.Assertions.assertEquals(300, store.codeExpireInSeconds());
        org.junit.jupiter.api.Assertions.assertEquals(60, store.nextAllowedInSeconds());
    }

    @Test
    @DisplayName("clearCode：删除验证码与失败计数（一次性）")
    void clearCodeRemovesBothKeys() {
        store.clearCode(SmsScene.DEVICE_VERIFY, "7");
        verify(redis).delete(anyList());
    }

    @Test
    @DisplayName("attempts：无记录 / 脏值 一律返回 0（不抛错）")
    void attemptsDefaultsToZero() {
        when(valueOps.get(ATTEMPT_KEY)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertEquals(0, store.attempts(SmsScene.LOGIN, PHONE));
        when(valueOps.get(ATTEMPT_KEY)).thenReturn("not-a-number");
        org.junit.jupiter.api.Assertions.assertEquals(0, store.attempts(SmsScene.LOGIN, PHONE));
    }
}
