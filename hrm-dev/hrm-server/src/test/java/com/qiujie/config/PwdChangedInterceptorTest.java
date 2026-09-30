package com.qiujie.config;

import com.qiujie.common.LoginUser;
import com.qiujie.enums.ErrorCode;
import com.qiujie.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARCH-C-7 服务端强制改密拦截单测：未改密拒访业务接口、白名单放行、旧会话标记缺失不拦截。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PwdChangedInterceptorTest {

    private PwdChangedInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PwdChangedInterceptor();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未改密访问业务接口 → 拦截并返回 200 + code 1111")
    void businessEndpointBlockedWhenPasswordNotChanged() throws Exception {
        loginWith(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(request, response, handlerMethod());

        assertFalse(pass);
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":" + ErrorCode.PASSWORD_CHANGE_REQUIRED.getCode()));
    }

    @Test
    @DisplayName("未改密访问白名单（改密/登出/读本人）→ 放行")
    void whitelistEndpointsPass() throws Exception {
        loginWith(false);
        assertTrue(interceptor.preHandle(request("PUT", "/api/v1/auth/password"), new MockHttpServletResponse(), handlerMethod()));
        assertTrue(interceptor.preHandle(request("POST", "/api/v1/auth/logout"), new MockHttpServletResponse(), handlerMethod()));
        assertTrue(interceptor.preHandle(request("GET", "/api/v1/auth/me"), new MockHttpServletResponse(), handlerMethod()));
    }

    @Test
    @DisplayName("已改密（true）访问业务接口 → 放行")
    void businessEndpointPassWhenPasswordChanged() throws Exception {
        loginWith(true);
        assertTrue(interceptor.preHandle(request("GET", "/api/v1/employees"), new MockHttpServletResponse(), handlerMethod()));
    }

    @Test
    @DisplayName("旧会话无标记（null）→ 放行（避免误锁存量用户）")
    void legacySessionWithoutFlagPasses() throws Exception {
        loginWith(null);
        assertTrue(interceptor.preHandle(request("GET", "/api/v1/employees"), new MockHttpServletResponse(), handlerMethod()));
    }

    @Test
    @DisplayName("未认证（UserContext 为空，公开端点）→ 不介入")
    void anonymousPasses() throws Exception {
        assertTrue(interceptor.preHandle(request("POST", "/api/v1/auth/login"), new MockHttpServletResponse(), handlerMethod()));
    }

    private void loginWith(Boolean pwdChanged) {
        LoginUser user = new LoginUser(1L, "admin", "ADMIN", "jti-1", "1");
        user.setPwdChanged(pwdChanged);
        UserContext.set(user);
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private HandlerMethod handlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(new DummyController(), DummyController.class.getDeclaredMethod("handle"));
    }

    /** 仅为构造 HandlerMethod 的占位控制器 */
    static class DummyController {
        public void handle() {
        }
    }
}
