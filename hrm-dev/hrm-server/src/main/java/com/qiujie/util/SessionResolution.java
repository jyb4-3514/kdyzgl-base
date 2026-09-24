package com.qiujie.util;

import com.qiujie.common.SessionInfo;

/**
 * 会话解析策略（纯逻辑，无 Redis 依赖，可离线单测）——JwtAuthFilter 的取值决策内核。
 * <p>
 * <b>sid 优先 + 旧 token 回退</b>（架构 §2.3.2）：
 * <ol>
 *   <li>先按 JWT {@code jti}（新 token 即 sid）查 {@code hrm:session:{sid}}；<b>命中即用 sid 会话</b>；</li>
 *   <li>sid 未命中 → 回退旧逻辑 {@code hrm:session:{employeeId}}（兼容部署前签发的旧 token，避免存量会话失效）；</li>
 *   <li>取到的会话必须与 JWT {@code jti} 一致，否则视为「已登出 / 被顶下线 / 被强制下线」→ 未解析（401）。</li>
 * </ol>
 * <p>
 * <b>回退分支的移除条件</b>（{@code JwtAuthFilter} 亦有 TODO(扩展) 标注）：待旧 token 全部自然过期
 * （最长 {@code hrm.auth.session-ttl-seconds}）且确认无存量旧会话后，可删除第 2 步回退与
 * {@link SessionUtil#get(Long)}/{@link SessionUtil#save(Long, SessionInfo)}/{@link SessionUtil#delete(Long)} 旧方法。
 */
public final class SessionResolution {

    /** 会话来源：SID=新 token（多端会话主体）；LEGACY=旧 token 回退（单键旧会话） */
    public enum Source {
        SID, LEGACY
    }

    /**
     * 解析结果。
     *
     * @param session 命中的会话（未命中为 {@code null}，调用方按 401 处理）
     * @param source  来源（未命中为 {@code null}）
     */
    public record Resolved(SessionInfo session, Source source) {

        /** 是否解析成功 */
        public boolean matched() {
            return session != null;
        }
    }

    private static final Resolved NONE = new Resolved(null, null);

    private SessionResolution() {
    }

    /**
     * 解析当前请求的会话。
     *
     * @param bySid        按 sid（JWT jti）查得的会话，未命中为 null
     * @param byEmployeeId 按 employeeId 查得的旧会话，未命中为 null
     * @param jwtJti       JWT 的 jti（新 token 即 sid）
     */
    public static Resolved resolve(SessionInfo bySid, SessionInfo byEmployeeId, String jwtJti) {
        if (bySid != null) {
            // sid 命中即以其为准：不再回退（避免「sid 会话存在但 jti 不匹配」时被旧键绕过）
            return jwtJti != null && jwtJti.equals(bySid.getJti())
                    ? new Resolved(bySid, Source.SID) : NONE;
        }
        if (byEmployeeId != null && jwtJti != null && jwtJti.equals(byEmployeeId.getJti())) {
            return new Resolved(byEmployeeId, Source.LEGACY);
        }
        return NONE;
    }
}
