package com.qiujie.util;

import com.qiujie.common.SessionInfo;
import com.qiujie.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 多端会话 Redis 操作单测（M1，Mockito 模拟 RedisTemplate，不需要真实 Redis）：
 * 会话主体写入、索引集合增删、同端同设备淘汰、强制下线全删、旧方法兼容。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SessionUtilTest {

    private static final long TTL_SECONDS = 259200L;
    private static final int MAX_SESSIONS = 5;
    private static final Long EMPLOYEE_ID = 7L;
    private static final String IDX_KEY = "hrm:session:idx:7";
    private static final String SID = "a".repeat(64);
    private static final String OLD_SID = "b".repeat(64);

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, Object> setOps = mock(SetOperations.class);

    private SessionUtil sessionUtil;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        AuthProperties properties = new AuthProperties();
        properties.setSessionTtlSeconds(TTL_SECONDS);
        properties.setMaxSessionsPerEmployee(MAX_SESSIONS);
        sessionUtil = new SessionUtil(redisTemplate, properties);
    }

    private SessionInfo info(String sid, String clientType, String deviceId, String loginTime) {
        SessionInfo info = new SessionInfo();
        info.setSid(sid);
        info.setJti(sid);
        info.setEmployeeId(String.valueOf(EMPLOYEE_ID));
        info.setUsername("u");
        info.setRole("STAFF");
        info.setClientType(clientType);
        info.setDeviceId(deviceId);
        info.setLoginTime(loginTime);
        return info;
    }

    @Test
    @DisplayName("saveBySid：写会话主体（TTL=配置值）+ 登记索引 + 刷新索引 TTL")
    void saveBySidWritesBodyAndIndex() {
        when(setOps.members(IDX_KEY)).thenReturn(null);
        SessionInfo info = info(SID, "web", "d1", "2026-09-24 10:00:00");

        sessionUtil.saveBySid(SID, info);

        verify(valueOps).set("hrm:session:" + SID, info, Duration.ofSeconds(TTL_SECONDS));
        verify(setOps).add(IDX_KEY, SID);
        verify(redisTemplate).expire(IDX_KEY, Duration.ofSeconds(TTL_SECONDS));
    }

    @Test
    @DisplayName("saveBySid：同端同设备重登 → 淘汰旧会话（删主体 + 从索引移除）")
    void saveBySidEvictsSameClientSameDevice() {
        when(setOps.members(IDX_KEY)).thenReturn(Set.<Object>of(OLD_SID));
        when(valueOps.get("hrm:session:" + OLD_SID)).thenReturn(info(OLD_SID, "web", "d1", "2026-09-24 09:00:00"));

        sessionUtil.saveBySid(SID, info(SID, "web", "d1", "2026-09-24 10:00:00"));

        verify(redisTemplate).delete("hrm:session:" + OLD_SID);
        verify(setOps).remove(IDX_KEY, OLD_SID);
        // Mockito 约束：同一调用里 matcher 与字面量不可混用，故字面量参数一律用 eq() 包裹
        verify(valueOps).set(eq("hrm:session:" + SID), any(SessionInfo.class), eq(Duration.ofSeconds(TTL_SECONDS)));
    }

    @Test
    @DisplayName("saveBySid：跨端不同设备 → 不淘汰，并存")
    void saveBySidKeepsCrossClient() {
        when(setOps.members(IDX_KEY)).thenReturn(Set.<Object>of(OLD_SID));
        when(valueOps.get("hrm:session:" + OLD_SID)).thenReturn(info(OLD_SID, "boss", "d2", "2026-09-24 09:00:00"));

        sessionUtil.saveBySid(SID, info(SID, "web", "d1", "2026-09-24 10:00:00"));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("getBySid：非法 sid（长度/字符不符）直接返回 null，不访问 Redis")
    void getBySidRejectsInvalidFormat() {
        assertNull(sessionUtil.getBySid("short"));
        assertNull(sessionUtil.getBySid("550e8400-e29b-41d4-a716-446655440000"));
        assertNull(sessionUtil.getBySid(null));
        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("getBySid：可读回同类型会话；值类型异常返回 null")
    void getBySidReadsSession() {
        SessionInfo stored = info(SID, "web", "d1", "t");
        when(valueOps.get("hrm:session:" + SID)).thenReturn(stored);
        assertSame(stored, sessionUtil.getBySid(SID));

        when(valueOps.get("hrm:session:" + SID)).thenReturn("not-a-session");
        assertNull(sessionUtil.getBySid(SID));
    }

    @Test
    @DisplayName("deleteBySid：删主体并从索引移除（会话存在时）")
    void deleteBySidRemovesBodyAndIndex() {
        when(valueOps.get("hrm:session:" + SID)).thenReturn(info(SID, "web", "d1", "t"));

        sessionUtil.deleteBySid(SID);

        verify(redisTemplate).delete("hrm:session:" + SID);
        verify(setOps).remove(IDX_KEY, SID);
    }

    @Test
    @DisplayName("deleteBySid：会话主体已不存在 → 仅删主体键（无法回推 employeeId），不触碰索引")
    void deleteBySidWhenMissing() {
        when(valueOps.get("hrm:session:" + SID)).thenReturn(null);

        sessionUtil.deleteBySid(SID);

        verify(redisTemplate).delete("hrm:session:" + SID);
        verifyNoInteractions(setOps);
    }

    @Test
    @DisplayName("listSids：索引缺失返回空集；有值返回 sid 集合；employeeId 为 null 返回空集")
    void listSids() {
        when(setOps.members(IDX_KEY)).thenReturn(null);
        assertTrue(sessionUtil.listSids(EMPLOYEE_ID).isEmpty());

        when(setOps.members(IDX_KEY)).thenReturn(Set.<Object>of(SID, OLD_SID));
        assertEquals(Set.of(SID, OLD_SID), sessionUtil.listSids(EMPLOYEE_ID));

        assertTrue(sessionUtil.listSids(null).isEmpty());
    }

    @Test
    @DisplayName("deleteAllOfEmployee：删除全部 sid 会话与索引键（强制下线）")
    void deleteAllOfEmployee() {
        when(setOps.members(IDX_KEY)).thenReturn(Set.<Object>of(SID, OLD_SID));

        sessionUtil.deleteAllOfEmployee(EMPLOYEE_ID);

        verify(redisTemplate).delete("hrm:session:" + SID);
        verify(redisTemplate).delete("hrm:session:" + OLD_SID);
        verify(redisTemplate).delete(IDX_KEY);
    }

    @Test
    @DisplayName("旧方法兼容：save/get/delete 仍操作 hrm:session:{employeeId} 单键")
    void legacyMethods() {
        SessionInfo legacy = info(SID, "web", "", "t");
        sessionUtil.save(EMPLOYEE_ID, legacy);
        verify(valueOps).set("hrm:session:7", legacy, Duration.ofSeconds(TTL_SECONDS));

        when(valueOps.get("hrm:session:7")).thenReturn(legacy);
        assertSame(legacy, sessionUtil.get(EMPLOYEE_ID));

        sessionUtil.delete(EMPLOYEE_ID);
        verify(redisTemplate).delete("hrm:session:7");
    }

    @Test
    @DisplayName("saveBySid 入参校验：sid 空或 info 为 null → 抛 IllegalArgumentException，不写 Redis")
    void saveBySidValidates() {
        assertThrows(IllegalArgumentException.class, () -> sessionUtil.saveBySid(null, info(SID, "web", "d1", "t")));
        assertThrows(IllegalArgumentException.class, () -> sessionUtil.saveBySid("  ", info(SID, "web", "d1", "t")));
        assertThrows(IllegalArgumentException.class, () -> sessionUtil.saveBySid(SID, null));
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }
}
