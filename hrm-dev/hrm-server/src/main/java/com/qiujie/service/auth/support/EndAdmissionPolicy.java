package com.qiujie.service.auth.support;

import java.util.Collection;
import java.util.Set;

/**
 * 端维度登录准入判定（纯逻辑，可离线单测）——<b>逐条对齐前端已实现的 Mock</b>
 * {@code hrm-demo/src/shared/mock/routes/auth.js#endAdmissionError}。
 * <p>
 * <b>口径</b>（demo-login-redesign §1.3 + 用户裁定）：
 * <ul>
 *   <li>{@code clientType} 缺省（未上报）→ <b>不校验</b>，行为与登录改造前完全一致（既有调用方 / 一期页面零影响）；</li>
 *   <li>{@code clientType=WEB}（PC 网页端）→ 仅允许 {@code hrm.auth.pc-allowed-roles} 内角色（默认 ADMIN），
 *       否则 1110；</li>
 *   <li>{@code clientType=H5} 且入口声明 {@code as=boss}（管理端视角）→ 仅允许 ADMIN / STATION_ADMIN，否则 1110；</li>
 *   <li>其余（{@code H5} 员工端、未知端类型）→ 不限角色。</li>
 * </ul>
 * <b>为什么不是安全边界（必读）</b>：{@code clientType} / {@code as} 均为客户端自称，<b>可被伪造或省略</b>。
 * 本判定属<b>产品 / 审计约束</b>；真正的安全边界恒为「角色 + 数据范围」——授权以服务端会话角色为唯一权威，
 * 伪造端类型不会获得任何额外权限（见 {@link com.qiujie.enums.ClientType} 类注释）。
 */
public final class EndAdmissionPolicy {

    /** PC 网页端标识（前端 {@code collectDevicePayload('WEB')} 上报值） */
    public static final String CLIENT_WEB = "WEB";
    /** 移动端标识（H5 壳 / 移动浏览器） */
    public static final String CLIENT_H5 = "H5";
    /** 管理端视角入口参数（移动端 {@code ?as=boss}） */
    public static final String AS_BOSS = "boss";

    /** 管理端（H5 + as=boss）允许的角色集合（对齐 Mock：ADMIN / STATION_ADMIN） */
    private static final Set<String> BOSS_VIEW_ROLES = Set.of("ADMIN", "STATION_ADMIN");

    private EndAdmissionPolicy() {
    }

    /**
     * 判定「该角色是否允许以该端登录」。
     *
     * @param clientType     请求体 {@code clientType}（可为 null / 空 / 未知；大小写与空白不敏感）
     * @param as             入口视角参数（如 {@code boss}；可为 null）
     * @param role           员工真实角色（服务端权威值）
     * @param pcAllowedRoles 配置的「PC 端允许角色」原始集合（可为 null / 含逗号分隔项 / 含非法项）
     * @return 允许返回 {@code true}；命中端约束且角色不满足返回 {@code false}（业务码 1110）
     */
    public static boolean isAllowed(String clientType, String as, String role, Collection<String> pcAllowedRoles) {
        String type = normalizeClientType(clientType);
        if (type.isEmpty()) {
            // 未上报端类型：不施加任何约束（保持既有 4 端点的改造前行为）
            return true;
        }
        String normalizedRole = normalizeToken(role);
        if (CLIENT_WEB.equals(type)) {
            return ClientRolePolicy.normalizeAllowedRoles(pcAllowedRoles).contains(normalizedRole);
        }
        // as 需按同一口径比较：AS_BOSS 常量为小写 "boss"，而 normalizeToken 会转大写，
        // 原 AS_BOSS.equals(normalizeToken(as)) 永不命中——boss 视图角色约束被静默跳过（M4 实跑 h5BossView 失败根因）。
        // 改为忽略大小写比较，消除「常量大小写」与「归一逻辑」不一致这一类缺陷。
        if (CLIENT_H5.equals(type) && AS_BOSS.equalsIgnoreCase(normalizeToken(as))) {
            // normalizeToken 保证非 null；空串不在集合内，天然拒绝
            return BOSS_VIEW_ROLES.contains(normalizedRole);
        }
        return true;
    }

    /**
     * 端类型归一：去空白 + 转大写；仅识别 {@code WEB} / {@code H5}，其余（含 null/空/未知）返回空串表示「不约束」。
     * 为什么未知端不抛错也不约束：端类型是弱信号，非法值不应影响登录（与 Mock「非 WEB/H5 即放行」一致）。
     */
    public static String normalizeClientType(String clientType) {
        String value = normalizeToken(clientType);
        return (CLIENT_WEB.equals(value) || CLIENT_H5.equals(value)) ? value : "";
    }

    /** 通用归一：null → ""，否则 trim + 转大写（避免对不可变集合传入 null 触发 NPE） */
    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
