package com.qiujie.filter;

import com.qiujie.common.LoginUser;
import com.qiujie.common.SessionInfo;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.SessionUtil;
import com.qiujie.util.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * 自研 JWT 认证过滤器（决策 D3/D10，流程见 api.md 3.1）：
 * 1. 白名单仅 /api/v1/auth/login，直接放行；
 * 2. 解析 JWT（签名 + 过期校验）→ 取 userId 与 jti；
 * 3. Redis 会话必须存在且 jti 一致（否则视为已登出/被顶下线/被强制下线 → 401）；
 * 4. 通过后注入 UserContext（ThreadLocal），请求结束清理，防止线程复用串号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 白名单：仅登录接口免认证 */
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final JwtUtil jwtUtil;
    private final SessionUtil sessionUtil;

    @Override
    public boolean shouldNotFilterErrorDispatch() {
        // 错误分发（forward 至 /error）不再做认证，避免覆盖已写出的错误响应
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 去掉 context-path 前缀后再匹配白名单，兼容不同部署路径
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());

        if (LOGIN_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        Claims claims;
        Long userId;
        try {
            claims = jwtUtil.parse(authorization.substring(7));
            userId = jwtUtil.getUserId(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // 签名错误/过期/格式非法：统一 401（不区分原因，避免暴露校验细节）
            writeUnauthorized(response);
            return;
        }
        if (userId == null || claims.getId() == null || claims.getSubject() == null) {
            writeUnauthorized(response);
            return;
        }

        // Redis 会话校验（外部依赖异常降级为 500，不与认证失败混淆）
        SessionInfo session;
        try {
            session = sessionUtil.get(userId);
        } catch (Exception e) {
            log.error("读取登录会话失败，userId={}", userId, e);
            writeSystemError(response);
            return;
        }
        // 会话不存在（登出/过期/被强制下线）或 jti 不一致（被新登录顶掉）→ 401
        if (session == null || !Objects.equals(session.getJti(), claims.getId())) {
            writeUnauthorized(response);
            return;
        }

        // 角色以 Redis 会话为准（服务端权威，Token 内 role 仅作参考）
        UserContext.set(new LoginUser(userId, session.getUsername(), session.getRole(), session.getJti()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /** 401：HTTP 401 + code 401 的 JSON（api.md 1.3 要求同步 HTTP 状态码） */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "未登录或登录态已失效");
    }

    /** Redis 等系统内部错误：HTTP 200 + code 500（与 api.md 1.3 系统错误映射一致） */
    private void writeSystemError(HttpServletResponse response) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, 500, "系统繁忙，请稍后重试");
    }

    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
