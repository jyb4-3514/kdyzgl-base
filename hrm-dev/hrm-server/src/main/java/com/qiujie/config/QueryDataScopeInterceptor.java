package com.qiujie.config;

import com.qiujie.common.LoginUser;
import com.qiujie.common.PublicEndpoints;
import com.qiujie.enums.RoleEnum;
import com.qiujie.util.DataScopeContext;
import com.qiujie.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * L1 数据范围收敛拦截器（对齐 Mock {@code domain/applyDataScope.js}，见架构 1.4.2 / ADR-02）：
 * 非 ADMIN 且非公开端点时，把「本人归属驿站」写入 {@link DataScopeContext}，由统一 Query 基类读取。
 * <p>
 * 只收敛 {@code stationId}，不收敛 {@code employeeId}；{@code auth:false} 端点与 ADMIN 放行。
 * 与 Mock 的差异：Mock 直接覆盖 handler 收到的 {@code params.stationId}；服务端在 HandlerInterceptor 中无法替换
 * request 对象，故以「上下文 + 统一基类读取」等价实现（见 {@link DataScopeContext} 类头说明）。
 */
@Slf4j
@Component
public class QueryDataScopeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 公开端点无归属概念，不收敛（对齐 Mock：user 为 null 时放行）
        if (PublicEndpoints.isPublic(currentPath(request))) {
            return true;
        }
        LoginUser user = UserContext.get();
        if (user == null) {
            // 未认证请求：交由 JwtAuthFilter 与 RequireRolesInterceptor 处理，这里不介入
            return true;
        }
        if (RoleEnum.isAdmin(user.getRole())) {
            // ADMIN 可跨驿站筛选，原样放行
            return true;
        }
        DataScopeContext.set(resolveConvergedStationId(user));
        return true;
    }

    /** 请求结束清理上下文（无论是否异常），防止线程复用串号 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataScopeContext.clear();
    }

    /**
     * 解析非 ADMIN 的收敛值。
     * 无归属（含旧会话尚未补齐）时按架构 8-2 推荐项 B 收敛为「无数据」，而不是放行全量。
     * TODO(扩展): 若用户裁定 8-2 为方案 A（拒绝），把无归属分支改为抛 BusinessException(FORBIDDEN)。
     */
    private Long resolveConvergedStationId(LoginUser user) {
        String stationId = user.getStationId();
        if (stationId == null || stationId.isBlank()) {
            log.warn("非 ADMIN 用户缺少归属驿站，数据范围收敛为「无数据」：userId={}, role={}",
                    user.getUserId(), user.getRole());
            return DataScopeContext.NO_DATA_STATION_ID;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            log.error("会话中的 stationId 非法，数据范围收敛为「无数据」：userId={}, stationId={}",
                    user.getUserId(), stationId);
            return DataScopeContext.NO_DATA_STATION_ID;
        }
    }

    /** 去掉 context-path 前缀后的请求路径（与白名单口径一致） */
    private String currentPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }
}
