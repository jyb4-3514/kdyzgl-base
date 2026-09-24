package com.qiujie.service.auth.support;

/**
 * 会话时效判定（纯逻辑，无 Spring / Redis 依赖，可离线单测）——M4「3 天到期强制重登录」的判定内核。
 * <p>
 * <b>口径</b>（security-auth-review §4.3 加固建议 ②）：
 * <ul>
 *   <li>时间一律取<b>服务端单调时钟</b>（{@code now} 由调用方以 {@code System.currentTimeMillis()/1000} 提供）；
 *       客户端时间概不采信，避免改系统时间绕过；</li>
 *   <li>认证时刻 {@code authEpochSeconds} 存于<b>服务端</b>会话（{@code SessionInfo.loginEpochSeconds}）或
 *       已验签的 JWT {@code iat}，二者均不可被客户端篡改；</li>
 *   <li>边界用「服务端单次请求时刻一次判定」：{@code now - auth >= window} 即视为已到期（恰好等于即到期），
 *       不做双端计时，避免竞态。</li>
 * </ul>
 * 判定为已到期时，调用方须返回专用码 {@code 1108}（HTTP 200 + code=1108），由前端分流强制重登。
 */
public final class SessionExpiryPolicy {

    private SessionExpiryPolicy() {
    }

    /**
     * 重认证窗口是否已过（应强制重新登录 → 1108）。
     *
     * @param authEpochSeconds 本次认证时刻（epoch 秒；{@code null} = 无权威时刻，判定为未到期以保持改造前行为，
     *                         如升级前写入的旧会话）
     * @param nowEpochSeconds  当前服务端时刻（epoch 秒）
     * @param windowSeconds    重认证窗口（{@code hrm.auth.periodic-reauth-seconds}；{@code <=0} 视为不启用窗口判定）
     * @return 已过窗口返回 {@code true}
     */
    public static boolean isWindowElapsed(Long authEpochSeconds, long nowEpochSeconds, long windowSeconds) {
        if (authEpochSeconds == null || windowSeconds <= 0) {
            return false;
        }
        // 差值可能为负（认证时刻晚于当前时刻，如时钟回拨）：视为未到期，绝不因负差放行到期判定
        return nowEpochSeconds - authEpochSeconds >= windowSeconds;
    }
}
