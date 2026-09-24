package com.qiujie.vo.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 登录响应（api.md 4.1.1；M4 登录契约改造）。
 * <p>
 * <b>契约兼容（硬约束）</b>：{@code token} / {@code expiresIn} / {@code employee} 的名称、类型、语义<b>不得变更</b>。
 * 新增字段一律<b>可选</b>（{@link JsonInclude.Include#NON_NULL}：为空即不出现在响应体，老前端忽略新字段仍可正常工作）：
 * <ul>
 *   <li>密码登录成功：{@code deviceTrusted=true}、{@code needDeviceVerify=false}、{@code sessionExpireAt}；</li>
 *   <li>密码登录遇新设备：{@code needDeviceVerify=true}、{@code deviceTrusted=false}、{@code twoFactorTicket}
 *       （<b>HTTP 200 + 正常响应体</b>承载，1104 仅为文案真源，不作错误返回），<b>无 token</b>；</li>
 *   <li>短信登录 / 设备二次验证成功：{@code deviceTrusted=true}、{@code sessionExpireAt}。</li>
 * </ul>
 * {@code expiresIn} 语义仍为「Token 有效期（秒）」，M4 取值由 86400 改为 259200（3 天，用户裁定）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginVO {

    private String token;

    /** Token 有效期（秒）；M4 起为 259200（3 天） */
    private Long expiresIn;

    private LoginEmployeeVO employee;

    // ==================== M4 新增可选字段（只增不减） ====================

    /** 当前设备是否已受信（服务端判定：Cookie 中的设备令牌摘要命中未撤销未过期记录） */
    private Boolean deviceTrusted;

    /** 是否需要新设备短信二次验证（true 时本次不签发会话，无 token） */
    private Boolean needDeviceVerify;

    /** 二次验证票据（仅 needDeviceVerify=true 时下发；短 TTL、一次性） */
    private String twoFactorTicket;

    /** 会话到期时刻（yyyy-MM-dd HH:mm:ss；= 认证时刻 + hrm.auth.session-ttl-seconds） */
    private String sessionExpireAt;

    /**
     * 本次下发的设备信任令牌<b>明文</b>（仅用于 Controller 写 Set-Cookie）。
     * <p><b>绝不进入响应体</b>：{@link JsonIgnore} 保证序列化跳过；库中只存其 SHA-256 摘要。
     */
    @JsonIgnore
    private String deviceToken;
}
