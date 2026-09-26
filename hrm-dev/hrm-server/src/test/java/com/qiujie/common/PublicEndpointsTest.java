package com.qiujie.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开端点白名单单测（C-08；M4 登录契约改造后更新为 6 条）。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class PublicEndpointsTest {

    @Test
    void loginAndAutoDispatchArePublic() {
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/login"));
        assertTrue(PublicEndpoints.isPublic("/api/v1/work-orders/auto-dispatch"));
    }

    @Test
    void m4AuthEndpointsArePublic() {
        // A1/A2/B2/D1（对齐 Mock routes/auth.js 的 auth:false 路由）
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/sms/send"));
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/sms/login"));
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/device/verify"));
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/captcha"));
    }

    @Test
    void registrationSubmitIsPublicButDetailIsNot() {
        // B3/M-6：R-2 提交为唯一净新增公开端点；R-3 详情 ADMIN-only，严禁进入白名单
        assertTrue(PublicEndpoints.isPublic("/api/v1/registration"));
        assertFalse(PublicEndpoints.isPublic("/api/v1/registration/RG-20260926-0001"));
    }

    @Test
    void deviceManagementEndpointsAreNotPublic() {
        // C1/C2 需登录态且仅限本人设备，严禁进入白名单
        assertFalse(PublicEndpoints.isPublic("/api/v1/auth/devices"));
        assertFalse(PublicEndpoints.isPublic("/api/v1/auth/devices/abc"));
    }

    @Test
    void businessEndpointsAreNotPublic() {
        assertFalse(PublicEndpoints.isPublic("/api/v1/employees"));
        assertFalse(PublicEndpoints.isPublic("/api/v1/auth/me"));
        assertFalse(PublicEndpoints.isPublic(null));
        // 前缀相近但不等价的路径不得放行
        assertFalse(PublicEndpoints.isPublic("/api/v1/auth/login/extra"));
        assertFalse(PublicEndpoints.isPublic("/api/v1/auth/sms/send/extra"));
    }

    @Test
    void whitelistHasExactlySevenEntries() {
        // M4：登录 + 企微自动派单（预留）+ A1/A2/B2/D1 = 6；B3 注册提交净新增 1 → 7
        assertEquals(7, PublicEndpoints.all().size());
    }
}
