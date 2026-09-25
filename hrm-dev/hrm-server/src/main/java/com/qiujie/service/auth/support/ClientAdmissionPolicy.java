package com.qiujie.service.auth.support;

import com.qiujie.enums.ClientType;
import com.qiujie.enums.RoleEnum;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 端准入判定（纯逻辑，无 Spring / Redis 依赖，可离线单测）——端类型归一 + 端 → 角色约束的<b>唯一真源</b>。
 * <p>
 * <b>目标准入矩阵（fail-closed）</b>：
 * <ul>
 *   <li>端 {@link ClientType#ADMIN}（PC 管理端 pc.html）→ 仅 {@code pc-allowed-roles}（默认 ADMIN）；</li>
 *   <li>端 {@link ClientType#BOSS}（管理端 H5 驿站精灵）→ 仅 {@code boss-allowed-roles}（默认 ADMIN）；</li>
 *   <li>端 {@link ClientType#STAFF}（员工端 H5 驿站助手）→ 仅 {@code staff-allowed-roles}（默认 STAFF + STATION_ADMIN）；</li>
 *   <li>端 {@link ClientType#WEB}（旧网页端 / 缺省值）→ 按 PC 口径取 {@code pc-allowed-roles}（纳入受约束端，不再放行）；</li>
 *   <li><b>缺省 / 未知 / 非法端 → 一律拒绝</b>（fail-closed，不做任何「不约束」分支）。</li>
 * </ul>
 * <p>
 * <b>为什么独立成纯函数</b>：端维度口径散在 {@code AuthServiceImpl} 的 if 链里漏判即静默放行（历史缺陷根因）；
 * 抽出后三条登录路径与票据复判共用同一对象，可逐条单测覆盖（见 {@code ClientAdmissionPolicyTest}）。
 * <p>
 * <b>为什么不是安全边界（必读）</b>：端类型来自客户端自称（{@code X-Client-Type} / {@code clientType} / {@code as}），
 * <b>可被伪造或省略</b>。本约束是<b>产品 / 审计约束</b>而非鉴权边界——真正的安全边界恒为「角色 + 数据范围」：
 * 授权判定以服务端持有的会话角色（{@code LoginUser.role}）为唯一权威，伪造端类型不会获得任何额外权限。
 * 收紧的意义在于消除「缺省即放行」的绕过口子，使「账号不能混登」成为<b>可预期</b>的产品约束。
 * <p>
 * <b>唯一归一入口</b>：{@link #resolveEnd} 是全仓唯一的端类型归一函数。本批已把原先并存的「未知回落 WEB」与
 * 「WEB / H5 + as」两套旧口径<b>删除</b>（相关旧类一并移除），此处之外不得再定义第二套取值域。
 */
public final class ClientAdmissionPolicy {

    /** 移动端历史端标识（旧前端上报 {@code clientType=H5}；不在 {@link ClientType} 取值域，按入口 {@code as} 派生端） */
    public static final String CLIENT_H5 = "H5";
    /** 管理端入口参数（移动端 {@code ?as=boss}） */
    public static final String AS_BOSS = "boss";

    private ClientAdmissionPolicy() {
    }

    /**
     * 端 → 允许角色集合的强类型载体（由 {@code AuthProperties} 组装）。
     * <p>
     * {@code pc} 兼管 PC 管理端（{@link ClientType#ADMIN}）与旧网页端（{@link ClientType#WEB}）；
     * {@code boss} 管管理端 H5；{@code staff} 管员工端 H5。任一为 {@code null} / 空即该端 fail-closed。
     */
    public record AllowedRoles(List<String> pc, List<String> boss, List<String> staff) {
    }

    /**
     * 端准入判定（密码登录 / 短信登录 / 设备二次验证复判 <b>三条路径共用同一策略对象</b>）。
     *
     * @param headerClientType 请求头 {@code X-Client-Type}（新契约；非空时优先，非法即拒，不回退请求体）
     * @param bodyClientType   请求体 {@code clientType}（兼容旧前端；请求头缺省时生效）
     * @param entryAs          入口视角参数（{@code boss} / {@code station}；仅旧端 {@code H5} 需要）
     * @param role             员工真实角色（服务端权威值，不做端类型推断）
     * @param allowed          三端允许角色集合（可为 null → fail-closed）
     * @return 允许登录返回 {@code true}；端不可判定或角色不匹配返回 {@code false}（业务码 1110）
     */
    public static boolean isLoginAllowed(String headerClientType, String bodyClientType, String entryAs,
                                         String role, AllowedRoles allowed) {
        ClientType end = resolveEnd(headerClientType, bodyClientType, entryAs);
        if (end == null) {
            // 缺省 / 未知 / 非法端一律 fail-closed（旧实现的「不约束」分支已删除）
            return false;
        }
        return roleSetOf(end, allowed).contains(normalizeToken(role));
    }

    /**
     * 端类型归一（全仓唯一入口）：请求头优先（新契约 {@code X-Client-Type}），请求体回退（兼容旧前端）。
     * <ul>
     *   <li>请求头非空即以请求头为准：命中取值域取该端；旧值 {@code H5} 按 {@code as} 派生；其余非法值返回 {@code null}；</li>
     *   <li>请求头缺失 / 空白时回退请求体，口径同上；</li>
     *   <li>两者皆缺省 → {@code null}（fail-closed）。</li>
     * </ul>
     *
     * @return 已知端；缺省 / 未知 / 非法一律 {@code null}（<b>不再回落 WEB</b>）
     */
    public static ClientType resolveEnd(String headerClientType, String bodyClientType, String entryAs) {
        String header = normalizeToken(headerClientType);
        if (!header.isEmpty()) {
            return parseEnd(header, entryAs);
        }
        String body = normalizeToken(bodyClientType);
        if (!body.isEmpty()) {
            return parseEnd(body, entryAs);
        }
        return null;
    }

    /** 该端是否移动端（设备平台归类用：BOSS / STAFF → H5，ADMIN / WEB → 网页端） */
    public static boolean isMobileEnd(ClientType end) {
        return end == ClientType.BOSS || end == ClientType.STAFF;
    }

    /**
     * 解析并归一允许角色集合（三端配置键共用）：
     * <ul>
     *   <li>逐项去前后空白、转大写（角色名恒为 ASCII 大写）；</li>
     *   <li>兼容逗号分隔标量（Spring 对逗号分隔值切分时<b>不做 trim</b>，故此处按逗号再切一次并逐段归一）；</li>
     *   <li>丢弃空段与非已知角色名（{@link RoleEnum#isValid}）——非法项不视为任何角色，避免拼写漂移静默放行。</li>
     * </ul>
     * 返回空集合表示「任何角色均不得从该端登录」（fail-closed）。
     *
     * @param raw 原始配置集合（可为 null；元素可为 null）
     * @return 归一后的允许角色集合（可变 {@link LinkedHashSet}，保留配置顺序）
     */
    public static Set<String> normalizeAllowedRoles(Collection<String> raw) {
        Set<String> allowed = new LinkedHashSet<>();
        if (raw == null) {
            return allowed;
        }
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            for (String part : item.split(",")) {
                String candidate = normalizeToken(part);
                if (RoleEnum.isValid(candidate)) {
                    allowed.add(candidate);
                }
            }
        }
        return allowed;
    }

    /** 原始端文本 → 端：命中取值域直接返回；旧值 {@code H5} 按入口 {@code as} 派生；其余返回 null（fail-closed） */
    private static ClientType parseEnd(String value, String entryAs) {
        ClientType end = ClientType.parse(value);
        if (end != null) {
            return end;
        }
        if (CLIENT_H5.equals(value)) {
            return AS_BOSS.equalsIgnoreCase(normalizeToken(entryAs)) ? ClientType.BOSS : ClientType.STAFF;
        }
        return null;
    }

    /** 端 → 允许角色集合；配置缺失 / 为空即空集（fail-closed） */
    private static Set<String> roleSetOf(ClientType end, AllowedRoles allowed) {
        if (allowed == null) {
            return Set.of();
        }
        return switch (end) {
            case ADMIN, WEB -> normalizeAllowedRoles(allowed.pc());
            case BOSS -> normalizeAllowedRoles(allowed.boss());
            case STAFF -> normalizeAllowedRoles(allowed.staff());
        };
    }

    /**
     * 通用归一：null → ""，否则 trim + 转大写。
     * <p>为什么 null 归一为空串而非保留 null：空串天然不在任何允许角色集合内（fail-closed），
     * 且避免对不可变集合传入 null 触发 NPE（服务器曾踩 {@code List.of(...).contains(null)}）。
     */
    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
