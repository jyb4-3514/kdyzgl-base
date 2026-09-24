package com.qiujie.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色枚举单测（C-01，口径对齐 Mock constants/role.js）。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class RoleEnumTest {

    @Test
    void onlyAdminIsAdmin() {
        assertTrue(RoleEnum.isAdmin("ADMIN"));
        assertFalse(RoleEnum.isAdmin("STATION_ADMIN"));
        assertFalse(RoleEnum.isAdmin("STAFF"));
        assertFalse(RoleEnum.isAdmin(null));
        assertFalse(RoleEnum.isAdmin("admin"));
    }

    @Test
    void isValidRecognisesThreeRolesOnly() {
        assertTrue(RoleEnum.isValid("ADMIN"));
        assertTrue(RoleEnum.isValid("STATION_ADMIN"));
        assertTrue(RoleEnum.isValid("STAFF"));
        assertFalse(RoleEnum.isValid("SUPER"));
        assertFalse(RoleEnum.isValid(null));
        assertFalse(RoleEnum.isValid(""));
    }
}
