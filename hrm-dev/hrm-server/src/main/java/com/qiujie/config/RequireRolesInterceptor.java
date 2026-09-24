package com.qiujie.config;

import com.qiujie.annotation.RequireAdmin;
import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.LoginUser;
import com.qiujie.common.PublicEndpoints;
import com.qiujie.enums.ErrorCode;
import com.qiujie.util.ResponseWriter;
import com.qiujie.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 端点级角色门槛拦截器（对齐 Mock engine 的 {@code roles} 校验，见架构 1.4.1）：
 * <ol>
 *   <li>方法级 {@code @RequireRoles} 优先，其次类级；</li>
 *   <li>兼容已废弃的 {@code @RequireAdmin}（语义等价于 {@code @RequireRoles({"ADMIN"})}）；</li>
 *   <li>公开端点（{@link PublicEndpoints}）放行；</li>
 *   <li><b>fail-closed</b>：非公开端点未声明任何角色门槛 → 403（杜绝「漏声明即静默放行」，与 Mock 加载期强校验同一纪律）。</li>
 * </ol>
 * 角色以 Redis 会话为准（由 {@code JwtAuthFilter} 注入 {@code UserContext}），Token 内 role 仅作参考。
 */
@Slf4j
@Component
public class RequireRolesInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String[] allowedRoles = resolveRoles(handlerMethod);
        if (allowedRoles == null) {
            // fail-closed：未声明门槛。公开端点直接放行，其余一律拒绝（配置错误须显式暴露）
            if (PublicEndpoints.isPublic(currentPath(request))) {
                return true;
            }
            log.error("端点未声明角色门槛，已按 fail-closed 拒绝：{} {}（请补 @RequireRoles 或加入公开白名单）",
                    request.getMethod(), currentPath(request));
            writeForbidden(response, ErrorCode.FORBIDDEN.getMessage());
            return false;
        }
        LoginUser user = UserContext.get();
        if (user == null || user.getRole() == null
                || Arrays.stream(allowedRoles).noneMatch(role -> role.equals(user.getRole()))) {
            writeForbidden(response, ErrorCode.FORBIDDEN.getMessage());
            return false;
        }
        return true;
    }

    /** 解析生效的角色门槛：方法级 → 类级 @RequireRoles → @RequireAdmin（等价 ADMIN）；无声明返回 null */
    private String[] resolveRoles(HandlerMethod handlerMethod) {
        RequireRoles requireRoles = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (requireRoles == null) {
            requireRoles = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
        }
        if (requireRoles != null) {
            return requireRoles.value();
        }
        boolean requireAdmin = handlerMethod.getMethodAnnotation(RequireAdmin.class) != null
                || handlerMethod.getBeanType().getAnnotation(RequireAdmin.class) != null;
        return requireAdmin ? new String[]{"ADMIN"} : null;
    }

    /** 去掉 context-path 前缀后的请求路径（与 JwtAuthFilter 白名单口径一致） */
    private String currentPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        ResponseWriter.write(response, HttpStatus.FORBIDDEN.value(), ErrorCode.FORBIDDEN.getCode(), message);
    }
}
