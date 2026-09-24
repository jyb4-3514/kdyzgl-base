package com.qiujie.service.auth.support;

/**
 * 受信设备可用性判定（纯逻辑，可离线单测）。
 * <p>
 * 与库表 {@code auth_trusted_device} 的三态字段一一对应：
 * <ul>
 *   <li>{@code trusted}：是否受信（1=是）；</li>
 *   <li>{@code revoked}：是否已撤销（1=是，软撤销保留审计）；</li>
 *   <li>{@code expires_at}：信任有效期截止（{@code hrm.auth.device-trust-ttl-seconds}，默认 30 天）。</li>
 * </ul>
 * <b>安全</b>：本判定只接受<b>服务端签发摘要</b>已匹配的行（匹配由 Mapper 查询完成），
 * 前端上报的弱信号（deviceId/platform/model…）<b>绝不参与</b>放行判定（security-auth-review §4.2）。
 */
public final class TrustedDevicePolicy {

    private TrustedDevicePolicy() {
    }

    /**
     * 该受信设备行当前是否可用于「跳过二次验证」。
     *
     * @param trusted              是否受信（1=是；null / 非 1 视为否）
     * @param revoked              是否已撤销（1=是；null 视为未撤销）
     * @param expiresAtEpochSec    信任有效期截止（epoch 秒；{@code null} = 未设，视为用配置周期兜底、当前有效）
     * @param nowEpochSec          当前服务端时刻（epoch 秒）
     * @return 可用返回 {@code true}
     */
    public static boolean isUsable(Integer trusted, Integer revoked, Long expiresAtEpochSec, long nowEpochSec) {
        if (trusted == null || trusted != 1) {
            return false;
        }
        if (revoked != null && revoked == 1) {
            return false;
        }
        if (expiresAtEpochSec == null) {
            return true;
        }
        // 边界：恰好到期即失效（严格小于才有效）
        return nowEpochSec < expiresAtEpochSec;
    }
}
