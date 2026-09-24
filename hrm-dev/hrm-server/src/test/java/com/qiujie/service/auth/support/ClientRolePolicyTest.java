package com.qiujie.service.auth.support;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端维度登录角色约束单测（纯逻辑，覆盖端类型缺失/非法/大小写、三角色 × 各端、配置空/非法/含空格）。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class ClientRolePolicyTest {

    /** 配置默认：仅 ADMIN 可登录 PC 管理端 */
    private static final List<String> DEFAULT_ALLOWED = List.of("ADMIN");

    @Test
    void pcClientAllowsAdminOnly() {
        assertTrue(ClientRolePolicy.isLoginAllowed("ADMIN", "ADMIN", DEFAULT_ALLOWED));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "STATION_ADMIN", DEFAULT_ALLOWED));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "STAFF", DEFAULT_ALLOWED));
    }

    @Test
    void nonPcClientsAreNotRoleConstrained() {
        // 缺失 / 空白 / 非法值（H5、unknown）→ 归一为 WEB；WEB/BOSS/STAFF 端均不限制角色
        String[] clients = {null, "", "   ", "WEB", "web", "STAFF", "BOSS", "H5", "unknown"};
        String[] roles = {"ADMIN", "STATION_ADMIN", "STAFF"};
        for (String client : clients) {
            for (String role : roles) {
                assertTrue(ClientRolePolicy.isLoginAllowed(client, role, DEFAULT_ALLOWED),
                        "端=" + client + " 角色=" + role + " 不应受角色约束");
            }
        }
    }

    @Test
    void constrainedClientIsTrimmedAndCaseInsensitive() {
        assertTrue(ClientRolePolicy.isRoleConstrainedClient("admin"));
        assertTrue(ClientRolePolicy.isRoleConstrainedClient(" Admin "));
        assertFalse(ClientRolePolicy.isRoleConstrainedClient("web"));
        assertFalse(ClientRolePolicy.isRoleConstrainedClient(null));
        assertFalse(ClientRolePolicy.isRoleConstrainedClient("H5"));
    }

    @Test
    void commaSeparatedScalarConfigIsAccepted() {
        // 模拟 Spring 把标量 "ADMIN,STATION_ADMIN" 绑定为单元素列表（Spring 切分不 trim）
        List<String> raw = List.of("ADMIN,STATION_ADMIN");
        assertTrue(ClientRolePolicy.isLoginAllowed("ADMIN", "ADMIN", raw));
        assertTrue(ClientRolePolicy.isLoginAllowed("ADMIN", "STATION_ADMIN", raw));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "STAFF", raw));
    }

    @Test
    void configEntriesAreTrimmedAndCaseInsensitive() {
        List<String> raw = List.of("ADMIN", " station_admin ");
        assertEquals(Set.of("ADMIN", "STATION_ADMIN"), ClientRolePolicy.normalizeAllowedRoles(raw));
        assertTrue(ClientRolePolicy.isLoginAllowed("ADMIN", "STATION_ADMIN", raw));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "STAFF", raw));
    }

    @Test
    void emptyOrNullConfigDeniesAllPcLogins() {
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "ADMIN", List.of()));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "ADMIN", Collections.emptyList()));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "ADMIN", null));
        // 非 PC 端不受空配置影响（fail-closed 仅作用于受约束端）
        assertTrue(ClientRolePolicy.isLoginAllowed("WEB", "STAFF", List.of()));
    }

    @Test
    void unknownRoleNamesInConfigAreDropped() {
        Set<String> allowed = ClientRolePolicy.normalizeAllowedRoles(
                List.of("ADMINN", "ADMIN", "super", "", "   "));
        assertEquals(Set.of("ADMIN"), allowed);
        // 仅含非法角色名的配置 → 归一为空集合 → PC 端登录一律拒绝
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "ADMIN", List.of("ADMINN")));
    }

    @Test
    void normalizeAllowedRolesSkipsNullElementsAndDeduplicates() {
        assertEquals(Set.of("ADMIN"),
                ClientRolePolicy.normalizeAllowedRoles(Arrays.asList("admin", " ADMIN ", null, "ADMIN")));
        assertTrue(ClientRolePolicy.normalizeAllowedRoles(null).isEmpty());
    }

    @Test
    void nullOrBlankRoleIsRejectedWithoutThrowing() {
        // 回归护栏：不得对不可变集合调用会抛 NPE 的方法（服务器曾踩 List.of(...).contains(null)）
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", null, List.of("ADMIN")));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "", List.of("ADMIN")));
        assertFalse(ClientRolePolicy.isLoginAllowed("ADMIN", "  ", List.of("ADMIN")));
    }
}
