package com.qiujie.config;

import com.qiujie.annotation.RequireAdmin;
import com.qiujie.common.LoginUser;
import com.qiujie.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ADMIN 权限拦截器（见 api.md 1.3 / requirement.md 3.3）：
 * 标注 @RequireAdmin 的方法/类，非 ADMIN 角色（以 Redis 会话中的角色为准）返回 HTTP 403 + code 403。
 */
@Component
public class RequireAdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 方法级注解优先，其次类级注解
        RequireAdmin requireAdmin = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        if (requireAdmin == null) {
            requireAdmin = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        }
        if (requireAdmin == null) {
            return true;
        }
        LoginUser user = UserContext.get();
        if (user == null || !"ADMIN".equals(user.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该资源\",\"data\":null}");
            return false;
        }
        return true;
    }
}
