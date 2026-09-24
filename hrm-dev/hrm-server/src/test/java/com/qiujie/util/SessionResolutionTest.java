package com.qiujie.util;

import com.qiujie.common.SessionInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话解析策略单测（M1）：sid 优先、旧 token 回退、jti 一致性、未命中。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SessionResolutionTest {

    private static SessionInfo session(String jti) {
        SessionInfo info = new SessionInfo();
        info.setJti(jti);
        return info;
    }

    @Test
    @DisplayName("sid 命中且 jti 一致 → 取 sid 会话（来源 SID），不回退旧键")
    void sidHit() {
        SessionInfo sidSession = session("jti-1");
        SessionResolution.Resolved resolved = SessionResolution.resolve(sidSession, session("jti-legacy"), "jti-1");
        assertTrue(resolved.matched());
        assertEquals(SessionResolution.Source.SID, resolved.source());
        assertSame(sidSession, resolved.session());
    }

    @Test
    @DisplayName("旧 token 回退：sid 未命中 → 取 employeeId 旧会话（来源 LEGACY），行为与改造前一致")
    void legacyFallback() {
        SessionInfo legacy = session("uuid-jti");
        SessionResolution.Resolved resolved = SessionResolution.resolve(null, legacy, "uuid-jti");
        assertTrue(resolved.matched());
        assertEquals(SessionResolution.Source.LEGACY, resolved.source());
        assertSame(legacy, resolved.session());
    }

    @Test
    @DisplayName("旧 token 回退：jti 不一致（被顶下线/强制下线）→ 未命中")
    void legacyJtiMismatch() {
        assertFalse(SessionResolution.resolve(null, session("other"), "uuid-jti").matched());
    }

    @Test
    @DisplayName("sid 命中但 jti 不一致 → 未命中（不以旧键绕过）")
    void sidHitButJtiMismatch() {
        SessionResolution.Resolved resolved = SessionResolution.resolve(session("other"), session("jti-1"), "jti-1");
        assertFalse(resolved.matched());
    }

    @Test
    @DisplayName("边界：两侧均未命中 / jti 为 null / 会话存在但会话内 jti 为 null → 未命中")
    void boundaries() {
        assertFalse(SessionResolution.resolve(null, null, "jti-1").matched());
        assertFalse(SessionResolution.resolve(null, session("jti-1"), null).matched());
        assertFalse(SessionResolution.resolve(session(null), null, "jti-1").matched());
        SessionResolution.Resolved none = SessionResolution.resolve(null, null, "jti-1");
        assertNull(none.source());
        assertNull(none.session());
    }
}
