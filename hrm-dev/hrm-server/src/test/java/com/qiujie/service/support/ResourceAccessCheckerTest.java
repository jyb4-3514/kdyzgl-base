package com.qiujie.service.support;

import com.qiujie.common.LoginUser;
import com.qiujie.enums.DataScopePolicy;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L3 资源归属校验单测（C-03，403/404 分界逐端点声明，禁止统一 → 架构 ADR-07）。
 * 纯逻辑（仅依赖 UserContext ThreadLocal），不依赖 Spring/DB。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class ResourceAccessCheckerTest {

    private final ResourceAccessChecker checker = new ResourceAccessChecker();

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void adminPassesAnyStation() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", ""));
        assertDoesNotThrow(() -> checker.check(999L, DataScopePolicy.NOT_FOUND));
        assertDoesNotThrow(() -> checker.check(999L, DataScopePolicy.FORBIDDEN));
    }

    @Test
    void nonAdminOwnStationPasses() {
        UserContext.set(new LoginUser(2L, "sa", "STATION_ADMIN", "jti", "3"));
        assertDoesNotThrow(() -> checker.check(3L, DataScopePolicy.FORBIDDEN));
    }

    @Test
    void nonAdminCrossStationReturns404WhenPolicyNotFound() {
        UserContext.set(new LoginUser(3L, "staff", "STAFF", "jti", "3"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> checker.check(4L, DataScopePolicy.NOT_FOUND));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void nonAdminCrossStationReturns403WhenPolicyForbidden() {
        UserContext.set(new LoginUser(3L, "staff", "STAFF", "jti", "3"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> checker.check(4L, DataScopePolicy.FORBIDDEN));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), e.getCode());
    }

    @Test
    void silentPolicySkipsCheck() {
        UserContext.set(new LoginUser(3L, "staff", "STAFF", "jti", "3"));
        assertDoesNotThrow(() -> checker.check(4L, DataScopePolicy.SILENT));
    }

    @Test
    void nullResourceStationIsTreatedAsForeign() {
        UserContext.set(new LoginUser(3L, "staff", "STAFF", "jti", "3"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> checker.check(null, DataScopePolicy.NOT_FOUND));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void nonAdminWithoutStationIsConvergedToForeign() {
        // 无归属非 ADMIN：任何资源都不在其可见范围（对齐架构 8-2 推荐 B）
        UserContext.set(new LoginUser(3L, "staff", "STAFF", "jti", ""));
        assertThrows(BusinessException.class, () -> checker.check(3L, DataScopePolicy.NOT_FOUND));
    }

    @Test
    void unauthenticatedIsRejectedWith401() {
        UserContext.clear();
        BusinessException e = assertThrows(BusinessException.class,
                () -> checker.check(3L, DataScopePolicy.NOT_FOUND));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
    }

    @Test
    void isVisibleReflectsScope() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", ""));
        assertTrue(checker.isVisible(999L));

        UserContext.set(new LoginUser(3L, "staff", "STAFF", "jti", "3"));
        assertTrue(checker.isVisible(3L));
        assertFalse(checker.isVisible(4L));
        assertFalse(checker.isVisible(null));

        UserContext.clear();
        assertFalse(checker.isVisible(3L));
    }
}
