package com.qiujie.service.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端维度登录准入单测（M4，逐条对齐前端 Mock {@code endAdmissionError}）：
 * WEB 仅 ADMIN；H5+boss 仅 ADMIN/STATION_ADMIN；H5 员工端不限；未上报端不校验；空值/非法值不抛错。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class EndAdmissionPolicyTest {

    private static final List<String> PC_ALLOWED = List.of("ADMIN");

    @Test
    @DisplayName("WEB（PC 网页端）：仅 ADMIN 允许，STAFF/STATION_ADMIN 拒绝（1110 触发面）")
    void webOnlyAdmin() {
        assertTrue(EndAdmissionPolicy.isAllowed("WEB", null, "ADMIN", PC_ALLOWED));
        assertFalse(EndAdmissionPolicy.isAllowed("WEB", null, "STAFF", PC_ALLOWED));
        assertFalse(EndAdmissionPolicy.isAllowed("WEB", null, "STATION_ADMIN", PC_ALLOWED));
    }

    @Test
    @DisplayName("WEB 大小写与空白不敏感：web / Web / 带空白 同样按 WEB 处理")
    void webIsCaseInsensitive() {
        assertTrue(EndAdmissionPolicy.isAllowed("web", null, "ADMIN", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed(" WeB ", null, "ADMIN", PC_ALLOWED));
        assertFalse(EndAdmissionPolicy.isAllowed("web", null, "STAFF", PC_ALLOWED));
    }

    @Test
    @DisplayName("H5 + as=boss（管理端视角）：ADMIN / STATION_ADMIN 允许，STAFF 拒绝")
    void h5BossView() {
        assertTrue(EndAdmissionPolicy.isAllowed("H5", "boss", "ADMIN", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("H5", "boss", "STATION_ADMIN", PC_ALLOWED));
        assertFalse(EndAdmissionPolicy.isAllowed("H5", "boss", "STAFF", PC_ALLOWED));
    }

    @Test
    @DisplayName("H5 员工端（无 as / as=staff|station）：不限角色")
    void h5StaffViewUnrestricted() {
        assertTrue(EndAdmissionPolicy.isAllowed("H5", null, "STAFF", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("H5", "staff", "STAFF", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("H5", "station", "STATION_ADMIN", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("H5", "boss", "ADMIN", List.of()));
    }

    @Test
    @DisplayName("未上报端类型（null/空/未知）：一律不校验（改造前行为不变）")
    void missingOrUnknownClientTypeIsUnrestricted() {
        assertTrue(EndAdmissionPolicy.isAllowed(null, null, "STAFF", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("", null, "STAFF", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("  ", null, "STAFF", PC_ALLOWED));
        assertTrue(EndAdmissionPolicy.isAllowed("ANDROID", null, "STAFF", PC_ALLOWED));
    }

    @Test
    @DisplayName("空值/非法值：角色 null、允许集合 null/空，均不抛错（无 NPE）；空集合 fail-closed")
    void nullAndIllegalValuesDoNotThrow() {
        // 角色 null → 归一为空串，不在集合内 → 拒绝，但不抛 NPE
        assertFalse(EndAdmissionPolicy.isAllowed("WEB", null, null, PC_ALLOWED));
        // 允许集合 null / 空 → 归一为空集 → fail-closed 拒绝
        assertFalse(EndAdmissionPolicy.isAllowed("WEB", null, "ADMIN", null));
        assertFalse(EndAdmissionPolicy.isAllowed("WEB", null, "ADMIN", List.of()));
    }

    @Test
    @DisplayName("逗号分隔标量 + 非法项：仅识别合法角色（大小写与空白归一）")
    void commaSeparatedAllowedRoles() {
        assertTrue(EndAdmissionPolicy.isAllowed("WEB", null, " admin ", List.of(" ADMIN ,admin, UNKNOWN ")));
        assertFalse(EndAdmissionPolicy.isAllowed("WEB", null, "STAFF", List.of(" ADMIN ,admin, UNKNOWN ")));
    }

    @Test
    @DisplayName("端类型归一：仅 WEB/H5 被识别，其余归为空串")
    void normalizeClientType() {
        assertEquals("WEB", EndAdmissionPolicy.normalizeClientType("web"));
        assertEquals("H5", EndAdmissionPolicy.normalizeClientType(" h5 "));
        assertEquals("", EndAdmissionPolicy.normalizeClientType("admin"));
        assertEquals("", EndAdmissionPolicy.normalizeClientType(null));
        assertEquals("", EndAdmissionPolicy.normalizeClientType(""));
    }
}
