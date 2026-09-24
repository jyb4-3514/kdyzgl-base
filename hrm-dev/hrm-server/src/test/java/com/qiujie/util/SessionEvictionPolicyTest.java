package com.qiujie.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多端会话淘汰策略单测（M1）：互踢粒度（端 + 设备）、并发上限淘汰最旧、边界。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SessionEvictionPolicyTest {

    private static SessionEvictionPolicy.Existing e(String sid, String clientType, String deviceId, String loginTime) {
        return new SessionEvictionPolicy.Existing(sid, clientType, deviceId, loginTime);
    }

    @Test
    @DisplayName("规则①：同端同设备重登 → 仅淘汰该旧会话（sid 不同，但端/设备一致）")
    void sameClientSameDeviceEvicted() {
        List<SessionEvictionPolicy.Existing> existing = List.of(
                e("old-web", "web", "d1", "2026-09-24 10:00:00"),
                e("boss-1", "boss", "d2", "2026-09-24 10:00:01"));
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("new-web", "WEB", "d1", "2026-09-24 11:00:00"), 5);
        assertEquals(List.of("old-web"), evictions);
    }

    @Test
    @DisplayName("跨端并存：同设备不同端不互踢；跨设备同端不互踢")
    void crossClientOrDeviceCoexist() {
        List<SessionEvictionPolicy.Existing> existing = List.of(
                e("web-d1", "web", "d1", "2026-09-24 10:00:00"),
                e("boss-d1", "boss", "d1", "2026-09-24 10:00:00"),
                e("web-d2", "web", "d2", "2026-09-24 10:00:00"));
        // 新会话：web + d3（与前三个端/设备均不同）
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("web-d3", "web", "d3", "2026-09-24 11:00:00"), 5);
        assertTrue(evictions.isEmpty(), "跨端/跨设备应并存，实际淘汰=" + evictions);
    }

    @Test
    @DisplayName("规则②：并发上限超限 → 淘汰最旧（按 loginTime 升序，同刻按 sid 升序）")
    void evictOldestWhenOverLimit() {
        List<SessionEvictionPolicy.Existing> existing = List.of(
                e("s-newest", "web", "d3", "2026-09-24 12:00:00"),
                e("s-oldest", "web", "d1", "2026-09-24 08:00:00"),
                e("s-middle", "web", "d2", "2026-09-24 10:00:00"));
        // 上限 3，写入新会话后共 4 个 → 淘汰最旧 1 个
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("s-incoming", "boss", "d9", "2026-09-24 13:00:00"), 3);
        assertEquals(List.of("s-oldest"), evictions);
    }

    @Test
    @DisplayName("并发上限：同刻 loginTime 时按 sid 升序确定性淘汰，且与互踢规则合并去重")
    void evictDeterministicAndDeduplicated() {
        List<SessionEvictionPolicy.Existing> existing = List.of(
                e("aaa", "web", "d1", "2026-09-24 10:00:00"),
                e("bbb", "web", "d2", "2026-09-24 10:00:00"),
                e("ccc", "web", "d3", "2026-09-24 10:00:00"));
        // 上限 2 → 写入后 4 个，超 2 → 淘汰最旧 2 个：同刻按 sid 升序 = aaa, bbb
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("incoming", "boss", "d9", "2026-09-24 10:00:00"), 2);
        assertEquals(List.of("aaa", "bbb"), evictions);
    }

    @Test
    @DisplayName("互踢与上限合并：同端同设备旧会话即使最新也应淘汰，且与上限淘汰去重")
    void kickAndLimitMerged() {
        List<SessionEvictionPolicy.Existing> existing = List.of(
                e("same-web-old", "web", "d1", "2026-09-24 12:00:00"),
                e("other-1", "web", "d2", "2026-09-24 08:00:00"),
                e("other-2", "web", "d3", "2026-09-24 09:00:00"));
        // 上限 2：incoming 为 web+d1 → 先踢 same-web-old；剩 other-1/other-2 + incoming = 3 > 2 → 再淘汰最旧 other-1
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("incoming", "web", "d1", "2026-09-24 13:00:00"), 2);
        assertEquals(List.of("same-web-old", "other-1"), evictions);
    }

    @Test
    @DisplayName("上限 <= 0 视为不限：只做互踢，不做上限淘汰")
    void nonPositiveLimitMeansUnlimited() {
        List<SessionEvictionPolicy.Existing> existing = List.of(
                e("keep-1", "web", "d1", "2026-09-24 08:00:00"),
                e("keep-2", "web", "d2", "2026-09-24 09:00:00"));
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("incoming", "boss", "d9", "2026-09-24 13:00:00"), 0);
        assertTrue(evictions.isEmpty());
    }

    @Test
    @DisplayName("边界：existing 为空/null、incoming 为 null、sid 自反（不淘汰自身）、脏数据跳过")
    void boundaries() {
        assertTrue(SessionEvictionPolicy.selectEvictions(null,
                e("incoming", "web", "d1", "t"), 5).isEmpty());
        assertTrue(SessionEvictionPolicy.selectEvictions(List.of(), null, 5).isEmpty());
        // 现有列表中已含 incoming 自身 → 不淘汰
        assertTrue(SessionEvictionPolicy.selectEvictions(
                List.of(e("incoming", "web", "d1", "t")), e("incoming", "web", "d1", "t"), 5).isEmpty());
        // 脏数据（null 元素 / null sid）跳过；loginTime 为 null 按最旧处理
        List<SessionEvictionPolicy.Existing> dirty = java.util.Arrays.asList(
                null, e(null, "web", "d1", "t"), e("s-null-time", "web", "d9", null),
                e("s-fresh", "web", "d8", "2026-09-24 10:00:00"));
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                dirty, e("incoming", "boss", "d0", "2026-09-24 11:00:00"), 2);
        // dirty 含 4 条，其中 null 元素与 null sid 被跳过 → 2 条有效 + incoming = 3 > 上限 2
        // → 淘汰最旧 1 条：loginTime=null 视为最旧，故为 s-null-time
        assertEquals(List.of("s-null-time"), evictions);
    }

    @Test
    @DisplayName("端类型大小写与空白归一：' WEB ' 与 'web' 视为同端")
    void clientTypeNormalized() {
        List<SessionEvictionPolicy.Existing> existing = List.of(e("old", " web ", "d1", "2026-09-24 10:00:00"));
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing, e("new", "WEB", "d1", "2026-09-24 11:00:00"), 5);
        assertEquals(List.of("old"), evictions);
    }
}
