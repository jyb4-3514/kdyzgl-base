package com.qiujie.service.auth.support;

import com.qiujie.enums.ClientType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端准入矩阵单测（对齐 {@code security-client-admission-review.md} 必改 2/3/4/5/6 与目标准入矩阵）。
 * <p>
 * 覆盖：12 组合（4 端 × 3 角色）、缺省 / 未知 / 非法端 fail-closed、旧端 {@code H5} 派生、请求头优先级、
 * 三路径共用同一策略、空配置 fail-closed、null 角色无 NPE、配置解析口径。
 * </p>
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class ClientAdmissionPolicyTest {

    /** 生产默认配置：PC 端（ADMIN/WEB）仅 ADMIN；管理端 H5（BOSS）仅 ADMIN；员工端 H5（STAFF）STAFF + STATION_ADMIN */
    private static final ClientAdmissionPolicy.AllowedRoles DEFAULT = new ClientAdmissionPolicy.AllowedRoles(
            List.of("ADMIN"), List.of("ADMIN"), List.of("STAFF,STATION_ADMIN"));

    // ==================== 12 组合矩阵（端 × 角色） ====================

    /**
     * 断言「某端对三种角色的准入结果」。
     * <p>
     * 为什么抽成辅助方法：矩阵用例按端逐行声明期望（真/假三元组）最易与目标准入矩阵对读，
     * 避免每格重复三行断言造成漏格；预期由调用方显式给出，不做任何推断。
     */
    private static void assertEnd(String rawEnd, boolean adminAllowed, boolean stationAdminAllowed, boolean staffAllowed) {
        assertEquals(adminAllowed,
                ClientAdmissionPolicy.isLoginAllowed(rawEnd, null, null, "ADMIN", DEFAULT), rawEnd + " + ADMIN");
        assertEquals(stationAdminAllowed,
                ClientAdmissionPolicy.isLoginAllowed(rawEnd, null, null, "STATION_ADMIN", DEFAULT), rawEnd + " + STATION_ADMIN");
        assertEquals(staffAllowed,
                ClientAdmissionPolicy.isLoginAllowed(rawEnd, null, null, "STAFF", DEFAULT), rawEnd + " + STAFF");
    }

    @Test
    @DisplayName("12 组合矩阵：4 端 × 3 角色，逐格与目标准入矩阵一致")
    void admissionMatrix() {
        // 端 ADMIN（PC 管理端 pc.html）→ 仅 ADMIN
        assertEnd("ADMIN", true, false, false);
        // 端 WEB（旧网页端 / 缺省值）→ 按 PC 口径，仅 ADMIN（必改 3）
        assertEnd("WEB", true, false, false);
        // 端 BOSS（管理端 H5 驿站精灵）→ 仅 ADMIN（必改 4：STATION_ADMIN 拒）
        assertEnd("BOSS", true, false, false);
        // 端 STAFF（员工端 H5 驿站助手）→ STAFF + STATION_ADMIN（必改 5：拒绝 ADMIN）
        assertEnd("STAFF", false, true, true);
    }

    @Test
    @DisplayName("旧端 H5 派生：H5+as=boss → 管理端；H5（无 as / as=station）→ 员工端")
    void legacyH5DerivesEndFromEntryAs() {
        // H5 + as=boss（管理端视角）→ BOSS 端：仅 ADMIN（STATION_ADMIN 由必改 4 收紧为拒）
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("H5", null, "boss", "STATION_ADMIN", DEFAULT));
        assertTrue(ClientAdmissionPolicy.isLoginAllowed("H5", null, "boss", "ADMIN", DEFAULT));
        // 入口参数大小写不敏感：as=BOSS 同样派生 BOSS 端
        assertTrue(ClientAdmissionPolicy.isLoginAllowed("H5", null, "BOSS", "ADMIN", DEFAULT));
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("H5", null, "BOSS", "STATION_ADMIN", DEFAULT));
        // H5（无 as / as=staff|station）→ STAFF 端：STAFF + STATION_ADMIN，拒 ADMIN
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("H5", null, null, "ADMIN", DEFAULT));
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("H5", null, "staff", "ADMIN", DEFAULT));
        assertTrue(ClientAdmissionPolicy.isLoginAllowed("H5", null, "station", "STAFF", DEFAULT));
        assertTrue(ClientAdmissionPolicy.isLoginAllowed("H5", null, null, "STATION_ADMIN", DEFAULT));
    }

    @Test
    @DisplayName("缺省 / 未知 / 非法端一律 fail-closed（三个角色均拒，必改 2）")
    void missingUnknownIllegalEndIsFailClosed() {
        List<String> badEnds = Arrays.asList(null, "", "   ", "ANDROID", "PC", "123", "WEB端");
        for (String raw : badEnds) {
            for (String role : List.of("ADMIN", "STATION_ADMIN", "STAFF")) {
                assertFalse(ClientAdmissionPolicy.isLoginAllowed(raw, null, null, role, DEFAULT),
                        "端=" + raw + " 角色=" + role + " 应被 fail-closed 拒绝");
                assertFalse(ClientAdmissionPolicy.isLoginAllowed(null, raw, null, role, DEFAULT),
                        "请求体端=" + raw + " 角色=" + role + " 应被 fail-closed 拒绝");
            }
        }
    }

    @Test
    @DisplayName("请求头 X-Client-Type 优先于请求体 clientType；头非法即拒（不回退请求体）")
    void headerTakesPrecedenceOverBody() {
        // 头 STAFF + 体 WEB：以头为准（STAFF 端）
        assertTrue(ClientAdmissionPolicy.isLoginAllowed("STAFF", "WEB", null, "STAFF", DEFAULT));
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("STAFF", "WEB", null, "ADMIN", DEFAULT));
        // 头缺省 → 用请求体
        assertTrue(ClientAdmissionPolicy.isLoginAllowed(null, "ADMIN", null, "ADMIN", DEFAULT));
        assertTrue(ClientAdmissionPolicy.isLoginAllowed("   ", "STAFF", null, "STAFF", DEFAULT));
        // 头非空但非法 → 直接拒，不回退请求体（fail-closed）
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("ANDROID", "ADMIN", null, "ADMIN", DEFAULT));
    }

    @Test
    @DisplayName("三条登录路径 + 票据复判共用同一策略：三种调用形状对同一矩阵结果一致（必改 6）")
    void threeLoginPathsShareSamePolicy() {
        for (String end : List.of("ADMIN", "BOSS", "STAFF", "WEB")) {
            for (String role : List.of("ADMIN", "STATION_ADMIN", "STAFF")) {
                boolean viaHeader = ClientAdmissionPolicy.isLoginAllowed(end, null, null, role, DEFAULT);   // 密码登录：读头
                boolean viaBody = ClientAdmissionPolicy.isLoginAllowed(null, end, null, role, DEFAULT);     // 短信登录：读体
                boolean viaTicket = ClientAdmissionPolicy.isLoginAllowed(null, end, null, role, DEFAULT);   // 票据复判：读票据
                assertEquals(viaHeader, viaBody, "密码/短信路径结果不一致：" + end + "+" + role);
                assertEquals(viaBody, viaTicket, "短信/票据路径结果不一致：" + end + "+" + role);
            }
        }
    }

    // ==================== 边界与配置解析 ====================

    @Test
    @DisplayName("空配置 / null 配置 fail-closed：该端任何角色均不得登录")
    void emptyOrNullRoleSetIsFailClosed() {
        ClientAdmissionPolicy.AllowedRoles empty = new ClientAdmissionPolicy.AllowedRoles(List.of(), List.of(), List.of());
        for (String end : List.of("ADMIN", "BOSS", "STAFF", "WEB")) {
            for (String role : List.of("ADMIN", "STATION_ADMIN", "STAFF")) {
                assertFalse(ClientAdmissionPolicy.isLoginAllowed(end, null, null, role, empty));
            }
        }
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("ADMIN", null, null, "ADMIN", null));
    }

    @Test
    @DisplayName("null / 空白角色不抛 NPE，且判为拒绝")
    void nullOrBlankRoleRejectedWithoutThrowing() {
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("ADMIN", null, null, null, DEFAULT));
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("ADMIN", null, null, "", DEFAULT));
        assertFalse(ClientAdmissionPolicy.isLoginAllowed("ADMIN", null, null, "  ", DEFAULT));
    }

    @Test
    @DisplayName("端类型归一 resolveEnd：规范化大小写 / 空白，未知返回 null")
    void resolveEndCanonicalises() {
        assertEquals(ClientType.ADMIN, ClientAdmissionPolicy.resolveEnd(" admin ", null, null));
        assertEquals(ClientType.STAFF, ClientAdmissionPolicy.resolveEnd(null, "h5", null));
        assertEquals(ClientType.BOSS, ClientAdmissionPolicy.resolveEnd(null, "H5", "boss"));
        assertNull(ClientAdmissionPolicy.resolveEnd(null, null, null));
        assertNull(ClientAdmissionPolicy.resolveEnd("ANDROID", null, null));
    }

    @Test
    @DisplayName("允许角色集合解析：逗号分隔、去空白、忽略大小写、丢弃非已知角色")
    void normalizeAllowedRolesParsing() {
        assertEquals(Set.of("STAFF", "STATION_ADMIN"),
                ClientAdmissionPolicy.normalizeAllowedRoles(List.of(" STAFF,station_admin ,Unknown,")));
        assertEquals(Set.of("ADMIN"),
                ClientAdmissionPolicy.normalizeAllowedRoles(Arrays.asList("admin", null, " ADMIN ")));
        assertTrue(ClientAdmissionPolicy.normalizeAllowedRoles(null).isEmpty());
        // 仅含非法角色名的配置 → 空集 → 该端 fail-closed
        assertTrue(ClientAdmissionPolicy.normalizeAllowedRoles(List.of("ADMINN", "super")).isEmpty());
    }
}
