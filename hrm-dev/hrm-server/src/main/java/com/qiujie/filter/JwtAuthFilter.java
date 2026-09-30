package com.qiujie.filter;

import com.qiujie.common.LoginUser;
import com.qiujie.common.PublicEndpoints;
import com.qiujie.common.SessionInfo;
import com.qiujie.config.AuthProperties;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.service.auth.support.SessionExpiryPolicy;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.ResponseWriter;
import com.qiujie.util.SessionResolution;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

/**
 * 自研 JWT 认证过滤器（决策 D3/D10，流程见 api.md 3.1；多端会话改造见 ADR-MC-03；3 天到期见 M4）：
 * 1. 公开端点白名单（{@link PublicEndpoints}）直接放行；
 * 2. 解析 JWT（签名 + 过期校验，含 {@code hrm.auth.expiry-grace-seconds} 到期宽限）→ 取 userId 与 jti；
 * 3. <b>会话解析优先 sid（jti）、未命中回退旧键 userId</b>：会话须存在且 jti 一致；
 * 4. <b>重认证窗口判定</b>：{@code now - 认证时刻 >= hrm.auth.periodic-reauth-seconds}（默认 3 天）→
 *    返回专用码 <b>1108</b>（HTTP 200 + code=1108，前端据此跳 {@code /login?expired=1&redirect=…} 强制重登）；
 *    未过窗口的「无会话」仍为 401（登出 / 被顶下线 / 被强制下线）；
 * 5. 通过后注入 UserContext（ThreadLocal，含 stationId 与端/设备维度），请求结束清理，防止线程复用串号。
 * <p>
 * <b>为什么 1108 与 401 必须区分</b>：模拟口径与前端实现（Mock {@code sessionMeta.expireAt}）一致——
 * 到期是「正常安全生命周期」（须提示并带 redirect），被下线是「凭据失效」（静默回登录页）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SessionUtil sessionUtil;
    private final EmployeeMapper employeeMapper;
    /** 认证与会话强类型配置（hrm.auth.*）：重认证窗口 */
    private final AuthProperties authProperties;

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

        if (PublicEndpoints.isPublic(path)) {
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
        String jwtJti = claims.getId();

        // Redis 会话校验（外部依赖异常降级为 500，不与认证失败混淆）
        // 解析顺序：① 先按 sid（= jti）查 hrm:session:{sid}；② 未命中回退旧键 hrm:session:{userId}（旧 token 兼容）
        SessionResolution.Resolved resolved;
        try {
            SessionInfo bySid = sessionUtil.getBySid(jwtJti);
            // TODO(扩展): 旧 token 回退分支——待存量旧会话全部自然过期（最长 hrm.auth.session-ttl-seconds）
            //   且确认无旧键会话后，可移除下行回退与 SessionUtil 的旧方法（get/save/delete(Long)），
            //   即完成「单会话模型」的彻底下线。移除前须确保不存在只写旧键的调用方。
            SessionInfo byEmployeeId = bySid == null ? sessionUtil.get(userId) : null;
            resolved = SessionResolution.resolve(bySid, byEmployeeId, jwtJti);
        } catch (Exception e) {
            log.error("读取登录会话失败，userId={}", userId, e);
            writeSystemError(response);
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long reauthWindowSeconds = authProperties.getPeriodicReauthSeconds();
        if (!resolved.matched()) {
            // 会话不存在或 jti 不一致：区分「重认证窗口已过」（1108）与「登出/被顶下线/被强制下线」（401）。
            // 为什么用 JWT iat：会话键已随 TTL 消失时，唯一可信的认证时刻在已验签的 Token 内（客户端不可篡改）。
            Long issuedAtSeconds = toEpochSeconds(claims.getIssuedAt());
            if (SessionExpiryPolicy.isWindowElapsed(issuedAtSeconds, nowSeconds, reauthWindowSeconds)) {
                writeSessionExpired(response);
            } else {
                writeUnauthorized(response);
            }
            return;
        }
        SessionInfo session = resolved.session();
        // 服务端权威的到期判定：超重认证窗口一律 1108（不依赖客户端时间，也不依赖 Token 是否仍在有效期内）
        if (SessionExpiryPolicy.isWindowElapsed(session.getLoginEpochSeconds(), nowSeconds, reauthWindowSeconds)) {
            writeSessionExpired(response);
            return;
        }

        // 角色以 Redis 会话为准（服务端权威，Token 内 role 仅作参考）；
        // 端类型/设备标识亦取自服务端会话（客户端自称的端类型不参与鉴权，避免伪造高权限端）
        String stationId = resolveStationId(userId, session, resolved);
        LoginUser loginUser = new LoginUser(userId, session.getUsername(), session.getRole(), session.getJti(), stationId,
                session.getClientType(), session.getDeviceId());
        // ARCH-C-7：注入首登改密标记，供 PwdChangedInterceptor 服务端强制拦截（未改密拦截业务接口）
        loginUser.setPwdChanged(resolvePwdChanged(userId, session, resolved));
        UserContext.set(loginUser);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 取会话中的归属驿站 id（C-02）。
     * <p>
     * 旧会话（本次升级前写入，无 stationId 字段）的兜底策略选择<b>回查员工表并回写</b>（而非强制重登）：
     * <ul>
     *   <li>不强制已登录的非 ADMIN 用户重登（无部署窗口的用户中断）；</li>
     *   <li>自愈：仅首次回查并写回会话，之后请求不再查库；</li>
     *   <li>兼顾「确无归属的员工」：回写空串 {@code ""} 作为「已解析」标记，与「字段缺失(null)」区分，避免反复查库；</li>
     *   <li>与清 Redis 会话方案相比不依赖易遗漏的运维动作（清理属 C 档）。</li>
     * </ul>
     * 回写路径按来源区分：sid 会话用 {@link SessionUtil#updateBySid}（仅刷新，不触发互踢/淘汰）；
     * 旧键会话沿用 {@link SessionUtil#save(Long, SessionInfo)}（覆盖写，语义与改造前一致）。
     * 回查失败时兜底为「无归属」，由 L1 收敛为「无数据」（安全侧，不放大可见范围）。
     */
    private String resolveStationId(Long userId, SessionInfo session, SessionResolution.Resolved resolved) {
        if (session.getStationId() != null) {
            return session.getStationId();
        }
        try {
            Employee employee = employeeMapper.selectById(userId);
            String stationText = employee == null || employee.getStationId() == null
                    ? ""
                    : String.valueOf(employee.getStationId());
            session.setStationId(stationText);
            // 回写会话（jti 不变，仅补齐字段）；写回失败不影响本次请求
            if (resolved.source() == SessionResolution.Source.SID) {
                sessionUtil.updateBySid(session.getJti(), session);
            } else {
                sessionUtil.save(userId, session);
            }
            return stationText;
        } catch (Exception e) {
            log.error("旧会话补齐 stationId 失败，本次按「无归属」处理，userId={}", userId, e);
            return "";
        }
    }

    /**
     * 取会话中的首登改密标记（ARCH-C-7）。
     * <p>
     * 会话已含标记则直接返回（新登录会话恒有值）。旧会话（本次升级前写入，无该字段）回查员工表补齐并回写，
     * 与 {@link #resolveStationId} 同「自愈」口径（仅首次回查，之后请求不再查库）。
     * <p>
     * 兜底：回查失败或员工不可查时按「已改密」放行——本拦截器是「首登强制改密」的业务闸门，
     * 不承担认证职责；认证失败已在上游拒绝，此处不应把认证成功用户误锁死（fail-open 仅限该过渡分支）。
     */
    private Boolean resolvePwdChanged(Long userId, SessionInfo session, SessionResolution.Resolved resolved) {
        if (session.getPwdChanged() != null) {
            return session.getPwdChanged();
        }
        try {
            Employee employee = employeeMapper.selectById(userId);
            boolean changed = employee == null || employee.getPwdChanged() == null || employee.getPwdChanged() == 1;
            session.setPwdChanged(changed);
            if (resolved.source() == SessionResolution.Source.SID) {
                sessionUtil.updateBySid(session.getJti(), session);
            } else {
                sessionUtil.save(userId, session);
            }
            return changed;
        } catch (Exception e) {
            log.error("旧会话补齐 pwdChanged 失败，本次按「已改密」放行，userId={}", userId, e);
            return true;
        }
    }

    /** 401：HTTP 401 + code 401 的 JSON（api.md 1.3 要求同步 HTTP 状态码） */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ResponseWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
    }

    /**
     * 1108：<b>HTTP 200 + code 1108</b>（与前端 Mock 完全一致）。
     * <p>
     * 为什么不走 401：1108 是「正常安全生命周期到期」，前端须据此显示「登录已到期」提示条并携带
     * {@code redirect} 回原路径；若混作 401，前端只能静默回登录页（丢失到期告知与回跳）。
     */
    private void writeSessionExpired(HttpServletResponse response) throws IOException {
        ResponseWriter.write(response, HttpStatus.OK.value(),
                ErrorCode.SESSION_EXPIRED.getCode(), ErrorCode.SESSION_EXPIRED.getMessage());
    }

    /** {@link Date} → epoch 秒（null 安全） */
    private Long toEpochSeconds(Date date) {
        return date == null ? null : date.getTime() / 1000L;
    }

    /** Redis 等系统内部错误：HTTP 200 + code 500（与 api.md 1.3 系统错误映射一致） */
    private void writeSystemError(HttpServletResponse response) throws IOException {
        ResponseWriter.write(response, HttpStatus.OK.value(),
                ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
