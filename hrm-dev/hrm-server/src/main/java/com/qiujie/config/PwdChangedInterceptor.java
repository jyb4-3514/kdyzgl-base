package com.qiujie.config;

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

import java.util.Set;

/**
 * 首登强制改密拦截器（ARCH-C-7 / 主代理裁定 A-⑤，评审 M-2）。
 * <p>
 * <b>为什么放服务端</b>：{@code pwd_changed} 此前仅作出参，前端据此进改密流程；高权限站长账号在弱初始口令下
 * 可直连接口绕过前端闸门，故须服务端 fail-closed 硬拦截。
 * <p>
 * <b>判定依据</b>：{@link UserContext} 中的 {@code pwdChanged}（由 {@code JwtAuthFilter} 从 Redis 会话注入，服务端权威）。
 * 未改密（{@code false}）访问非白名单业务接口即拒；{@code true} 或 {@code null}（旧会话未标记）放行——
 * {@code null} 放行是为避免升级期误锁存量用户，其权威值由登录时写入、或由 {@code JwtAuthFilter} 自愈补齐。
 * <p>
 * <b>白名单</b>：公开端点全量（由 {@code JwtAuthFilter} 直接放行，此处 {@link UserContext} 为空亦放行）
 * + 改本人密码 + 登出 + 读本人。除白名单外的一切业务接口在未改密前一律拒访。
 * <p>
 * <b>链序</b>：注册于 {@code JwtAuthFilter} 之后（MVC 拦截器在过滤器链内层），先于业务处理。
 */
@Slf4j
@Component
public class PwdChangedInterceptor implements HandlerInterceptor {

    /** 未改密时仍须放行的端点（改密 / 登出 / 读本人；公开端点另由 {@link PublicEndpoints} 覆盖） */
    private static final Set<String> WHITELIST = Set.of(
            "/api/v1/auth/password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        LoginUser user = UserContext.get();
        if (user == null) {
            // 公开端点 / 未认证：认证与角色由 JwtAuthFilter 与 RequireRolesInterceptor 负责，这里不介入
            return true;
        }
        Boolean pwdChanged = user.getPwdChanged();
        // 已改密或旧会话未标记（null，避免误锁存量用户）→ 放行
        if (pwdChanged == null || pwdChanged) {
            return true;
        }
        String path = currentPath(request);
        if (PublicEndpoints.isPublic(path) || WHITELIST.contains(path)) {
            return true;
        }
        log.info("首登未改密拦截业务接口：userId={}, {} {}", user.getUserId(), request.getMethod(), path);
        writePasswordChangeRequired(response);
        return false;
    }

    /** 去掉 context-path 前缀后的请求路径（与白名单口径一致） */
    private String currentPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }

    /** HTTP 200 + code 1111（与 1108/1110 同段同映射）：前端据业务码引导进入改密流程 */
    private void writePasswordChangeRequired(HttpServletResponse response) throws Exception {
        ResponseWriter.write(response, HttpStatus.OK.value(),
                ErrorCode.PASSWORD_CHANGE_REQUIRED.getCode(), ErrorCode.PASSWORD_CHANGE_REQUIRED.getMessage());
    }
}
