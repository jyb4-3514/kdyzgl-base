package com.qiujie.service.auth.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.entity.AuthTrustedDevice;
import com.qiujie.mapper.AuthTrustedDeviceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 受信设备登记与撤销单测（M4，Mockito 模拟 Mapper，无需数据库）：
 * 摘要匹配前提、幂等 upsert 字段、撤销、改密/禁用致全量失效。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class TrustedDeviceRegistryTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 纯单测无 Spring/MyBatis-Plus 上下文：LambdaUpdateWrapper.set(...) 会即时解析列名，
        // 未注册实体 TableInfo（含 lambda 缓存）即抛 "can not find lambda cache for this entity"（M4 实跑 3 个 error 根因）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuthTrustedDevice.class);
    }

    private static final Long EMPLOYEE_ID = 7L;
    private static final String FINGERPRINT = "f".repeat(64);
    private static final String TOKEN_HASH = "a".repeat(64);

    private final AuthTrustedDeviceMapper mapper = mock(AuthTrustedDeviceMapper.class);
    private final TrustedDeviceRegistry registry = new TrustedDeviceRegistry(mapper);

    @Test
    @DisplayName("findUsableByToken：令牌摘要为空 → 不查库直接返回 null（绝不匹配任何行）")
    void findUsableRequiresTokenHash() {
        assertNull(registry.findUsableByToken(EMPLOYEE_ID, null, 1L));
        assertNull(registry.findUsableByToken(EMPLOYEE_ID, "  ", 1L));
        assertNull(registry.findUsableByToken(null, TOKEN_HASH, 1L));
        verify(mapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("findUsableByToken：命中且未过期 → 返回该行；已过期 → 返回 null")
    void findUsableRespectsExpiry() {
        long now = 1_700_000_000L;
        AuthTrustedDevice row = trustedRow(LocalDateTime.now().plusDays(30));
        when(mapper.selectOne(any())).thenReturn(row);
        assertNotNull(registry.findUsableByToken(EMPLOYEE_ID, TOKEN_HASH, now));

        row.setExpiresAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        assertNull(registry.findUsableByToken(EMPLOYEE_ID, TOKEN_HASH, now));
    }

    @Test
    @DisplayName("upsertTrusted：无既有行 → 插入受信行（trusted=1/revoked=0/摘要与指纹入参一致）")
    void upsertInsertsNewTrustedRow() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of());

        AuthTrustedDevice saved = registry.upsertTrusted(EMPLOYEE_ID, FINGERPRINT, TOKEN_HASH,
                "dev-1", "WEB", "model-x", "os-1", "1.0", "1.2.3.4", LocalDateTime.now(),
                2_592_000L, 5);

        ArgumentCaptor<AuthTrustedDevice> captor = ArgumentCaptor.forClass(AuthTrustedDevice.class);
        verify(mapper).insert(captor.capture());
        AuthTrustedDevice inserted = captor.getValue();
        assertEquals(EMPLOYEE_ID, inserted.getEmployeeId());
        assertEquals(FINGERPRINT, inserted.getDeviceFingerprint());
        assertEquals(TOKEN_HASH, inserted.getDeviceTokenHash());
        assertEquals(1, inserted.getTrusted());
        assertEquals(0, inserted.getRevoked());
        assertNotNull(inserted.getFirstSeenTime());
        assertNotNull(inserted.getExpiresAt());
        assertEquals(inserted, saved);
    }

    @Test
    @DisplayName("upsertTrusted：既有行（含已撤销）→ 更新为受信并清空 revokedAt（重信复用同一行）")
    void upsertReactivatesExistingRow() {
        AuthTrustedDevice existing = trustedRow(LocalDateTime.now().plusDays(1));
        existing.setId(99L);
        existing.setRevoked(1);
        existing.setRevokedAt(LocalDateTime.now().minusDays(1));
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.selectList(any())).thenReturn(List.of());

        registry.upsertTrusted(EMPLOYEE_ID, FINGERPRINT, TOKEN_HASH,
                "dev-1", "WEB", null, null, null, "1.2.3.4", LocalDateTime.now(),
                2_592_000L, 5);

        verify(mapper).update(any(), any());
        assertEquals(1, existing.getTrusted());
        assertEquals(0, existing.getRevoked());
        assertNull(existing.getRevokedAt());
    }

    @Test
    @DisplayName("revokeByDeviceId：命中并撤销返回 true；无匹配返回 false；空入参不查库")
    void revokeByDeviceId() {
        when(mapper.update(any(), any())).thenReturn(1);
        assertTrue(registry.revokeByDeviceId(EMPLOYEE_ID, "dev-1", LocalDateTime.now()));

        when(mapper.update(any(), any())).thenReturn(0);
        assertFalse(registry.revokeByDeviceId(EMPLOYEE_ID, "dev-x", LocalDateTime.now()));

        assertFalse(registry.revokeByDeviceId(EMPLOYEE_ID, "  ", LocalDateTime.now()));
        assertFalse(registry.revokeByDeviceId(null, "dev-1", LocalDateTime.now()));
    }

    @Test
    @DisplayName("revokeAllOfEmployee（改密/禁用/删除→设备全失效）：返回影响行数；null 入参返回 0 不查库")
    void revokeAllOfEmployee() {
        when(mapper.update(any(), any())).thenReturn(3);
        assertEquals(3, registry.revokeAllOfEmployee(EMPLOYEE_ID));

        assertEquals(0, registry.revokeAllOfEmployee(null));
    }

    @Test
    @DisplayName("listActiveByEmployee：null 入参返回空集且不查库")
    void listActiveByEmployeeGuardsNull() {
        assertTrue(registry.listActiveByEmployee(null).isEmpty());
        verify(mapper, never()).selectList(any());
    }

    private AuthTrustedDevice trustedRow(LocalDateTime expiresAt) {
        AuthTrustedDevice row = new AuthTrustedDevice();
        row.setEmployeeId(EMPLOYEE_ID);
        row.setDeviceFingerprint(FINGERPRINT);
        row.setDeviceTokenHash(TOKEN_HASH);
        row.setTrusted(1);
        row.setRevoked(0);
        row.setExpiresAt(expiresAt);
        return row;
    }
}
