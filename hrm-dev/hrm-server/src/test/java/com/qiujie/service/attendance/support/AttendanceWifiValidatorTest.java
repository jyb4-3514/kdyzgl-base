package com.qiujie.service.attendance.support;

import com.qiujie.entity.WifiEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WiFi 白名单校验/归一纯逻辑单测（边界：空/超长/刚好 32、MAC 格式、区分大小写去重、条数上限、空串 bssid 归一）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceWifiValidatorTest {

    @Test
    @DisplayName("未提交 wifiList（null）→ 不校验，返回 null")
    void nullListSkips() {
        assertNull(AttendanceWifiValidator.validate(null));
    }

    @Test
    @DisplayName("单条合法（ssid 非空 + bssid 可空）→ 通过")
    void validSingle() {
        assertNull(AttendanceWifiValidator.validate(List.of(entry("Express-WiFi", "AC:84:C6:00:00:02"))));
        assertNull(AttendanceWifiValidator.validate(List.of(entry("Express-WiFi", null))));
        assertNull(AttendanceWifiValidator.validate(List.of(entry("Express-WiFi", ""))));
    }

    @Test
    @DisplayName("ssid 为空/纯空白/null、条目为 null → 逐条拒绝")
    void ssidRequired() {
        assertEquals("WiFi 名称不可为空", AttendanceWifiValidator.validate(List.of(entry(null, null))));
        assertEquals("WiFi 名称不可为空", AttendanceWifiValidator.validate(List.of(entry("   ", null))));
        List<WifiEntry> withNull = new ArrayList<>();
        withNull.add(null);
        assertEquals("WiFi 白名单条目不可为空", AttendanceWifiValidator.validate(withNull));
    }

    @Test
    @DisplayName("ssid 长度：33 拒绝、刚好 32 通过、trim 后计入长度")
    void ssidLength() {
        assertEquals("WiFi 名称须为 1-32 个字符", AttendanceWifiValidator.validate(List.of(entry("a".repeat(33), null))));
        assertNull(AttendanceWifiValidator.validate(List.of(entry("a".repeat(32), null))));
        // 首尾空白不计入长度：30 字符 + 两侧空白 → trim 后 30，合法
        assertNull(AttendanceWifiValidator.validate(List.of(entry("  " + "a".repeat(30) + "  ", null))));
    }

    @Test
    @DisplayName("bssid：非法 MAC 拒绝；合法 MAC 大小写混合通过；空串视为未填")
    void bssidFormat() {
        assertEquals("BSSID 须为 AA:BB:CC:DD:EE:FF 格式",
                AttendanceWifiValidator.validate(List.of(entry("WiFi", "AC-84-C6-00-00-02"))));
        assertEquals("BSSID 须为 AA:BB:CC:DD:EE:FF 格式",
                AttendanceWifiValidator.validate(List.of(entry("WiFi", "AC:84:C6:00:00"))));
        assertEquals("BSSID 须为 AA:BB:CC:DD:EE:FF 格式",
                AttendanceWifiValidator.validate(List.of(entry("WiFi", "ZZ:84:C6:00:00:02"))));
        // 大小写不敏感
        assertNull(AttendanceWifiValidator.validate(List.of(entry("WiFi", "ac:84:c6:00:00:02"))));
        assertNull(AttendanceWifiValidator.validate(List.of(entry("WiFi", "Ac:84:c6:00:00:02"))));
    }

    @Test
    @DisplayName("去重：完全相同的 ssid 判重；仅大小写不同视为不同（落到条数上限而非重复）")
    void duplicate() {
        assertEquals("WiFi 名称重复：SSID",
                AttendanceWifiValidator.validate(List.of(entry("SSID", null), entry("SSID", null))));
        // 区分大小写：'SSID' 与 'ssid' 不是重复项（若能通过条数上限则应通过——此处置于 2 条会命中条数上限，
        // 故断言其命中的是「条数上限」而非「重复」，以此证明二者未被判为同一 ssid）
        assertEquals("WiFi 白名单同一驿站仅允许配置 1 条",
                AttendanceWifiValidator.validate(List.of(entry("SSID", null), entry("ssid", null))));
    }

    @Test
    @DisplayName("条数上限：2 条不同 ssid → 拒绝（每站至多 1 条）")
    void maxEntries() {
        assertEquals("WiFi 白名单同一驿站仅允许配置 1 条",
                AttendanceWifiValidator.validate(List.of(entry("WiFi-A", null), entry("WiFi-B", null))));
        // 去重与条数同时命中时，先报字段级/重复错误（顺序：逐条字段 → 去重 → 条数）
        assertEquals("WiFi 名称重复：WiFi-A",
                AttendanceWifiValidator.validate(List.of(entry("WiFi-A", null), entry("WiFi-A", null), entry("WiFi-B", null))));
    }

    @Test
    @DisplayName("归一：ssid trim；bssid 空串/纯空白 → null；不改原对象；MAC 保留原大小写")
    void normalize() {
        WifiEntry raw = entry("  Express-WiFi  ", "  ac:84:c6:00:00:02  ");
        List<WifiEntry> normalized = AttendanceWifiValidator.normalize(List.of(raw));
        assertEquals(1, normalized.size());
        assertEquals("Express-WiFi", normalized.get(0).getSsid());
        assertEquals("ac:84:c6:00:00:02", normalized.get(0).getBssid());
        // 返回新对象，原入参不被修改
        assertNotSame(raw, normalized.get(0));
        assertEquals("  Express-WiFi  ", raw.getSsid());

        assertNull(AttendanceWifiValidator.normalize(List.of(entry("WiFi", ""))).get(0).getBssid());
        assertNull(AttendanceWifiValidator.normalize(List.of(entry("WiFi", "   "))).get(0).getBssid());
        assertNull(AttendanceWifiValidator.normalize(List.of(entry("WiFi", null))).get(0).getBssid());
    }

    // ==================== 夹具 ====================

    private static WifiEntry entry(String ssid, String bssid) {
        WifiEntry wifi = new WifiEntry();
        wifi.setSsid(ssid);
        wifi.setBssid(bssid);
        return wifi;
    }
}
