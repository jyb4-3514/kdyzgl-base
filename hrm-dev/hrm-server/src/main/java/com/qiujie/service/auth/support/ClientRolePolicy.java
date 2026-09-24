package com.qiujie.service.auth.support;

import com.qiujie.enums.ClientType;
import com.qiujie.enums.RoleEnum;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 端维度登录角色约束（纯逻辑，无 Spring / Redis 依赖，可离线单测）。
 * <p>
 * <b>约束内容</b>：PC 管理端（{@link ClientType#ADMIN}）仅允许 {@code hrm.auth.pc-allowed-roles}
 * 集合内的角色登录（默认 {@code ADMIN}）；站长/员工声明 PC 管理端登录一律拒绝。
 * <p>
 * <b>为什么独立成纯函数</b>：端维度角色口径属审计项，散在 {@code AuthServiceImpl} 的 if 链里漏判即静默放行；
 * 抽出后可逐条单测覆盖（见 {@code ClientRolePolicyTest}），与 P7 {@code LeaveAccessPolicy}、
 * P8 {@code WorkOrderAccessPolicy} 同法。
 * <p>
 * <b>为什么不是安全边界（必读）</b>：端类型来自客户端自称的 {@code X-Client-Type} 头，<b>可被伪造或省略</b>。
 * 故本约束是<b>产品 / 审计约束</b>，而非鉴权边界——真正的安全边界恒为「角色 + 数据范围」：授权判定以服务端
 * 持有的会话角色（{@code LoginUser.role}）为唯一权威。伪造端类型（如声明 H5 → 归一为 {@link ClientType#WEB}）
 * 以绕过本校验者，<b>其权限仍由 role 决定，不会获得任何额外权限</b>（见 {@link ClientType} 类注释）。
 * <p>
 * <b>为什么仅约束 PC 管理端</b>：不传 {@code X-Client-Type} 时回落 {@link ClientType#WEB}，
 * 须保持与改造前一致的行为（WEB 不限制角色），故本类只对 {@link ClientType#ADMIN} 施加角色约束。
 *
 * TODO(扩展): 若产品要求「缺省 WEB 端同样仅允许 ADMIN」，只需在 {@link #isRoleConstrainedClient} 的
 *   受约束端集合中加入 {@link ClientType#WEB}；该改动会收紧无头客户端的登录行为，属契约/口径变更，
 *   须先经用户确认并同步 {@code api.md}，本批不引入。
 */
public final class ClientRolePolicy {

    private ClientRolePolicy() {
    }

    /**
     * 判定「该角色是否允许以该端类型登录」。
     *
     * @param rawClientType   原始 {@code X-Client-Type} 头值（可为 null / 空 / 非法，按 {@link ClientType#normalize} 归一）
     * @param role            员工真实角色（服务端权威值，不做端类型推断）
     * @param pcAllowedRoles  配置的「PC 管理端允许角色」原始集合（可为 null / 空 / 含逗号分隔项 / 含非法项）
     * @return 允许登录返回 {@code true}；受约束端且角色不在允许集合内返回 {@code false}
     */
    public static boolean isLoginAllowed(String rawClientType, String role, Collection<String> pcAllowedRoles) {
        if (!isRoleConstrainedClient(rawClientType)) {
            return true;
        }
        return normalizeAllowedRoles(pcAllowedRoles).contains(normalizeRole(role));
    }

    /**
     * 该端类型是否受「端维度角色约束」。
     * 仅 PC 管理端受约束；未知/缺失/非法端类型一律归一为 {@link ClientType#WEB}（不受约束），保持改造前行为。
     */
    public static boolean isRoleConstrainedClient(String rawClientType) {
        return ClientType.ADMIN.name().equals(ClientType.normalize(rawClientType));
    }

    /**
     * 解析并归一「PC 管理端允许角色集合」：
     * <ul>
     *   <li>逐项去前后空白、转大写（角色名恒为 ASCII 大写）；</li>
     *   <li>兼容逗号分隔标量（Spring 对逗号分隔值切分时<b>不做 trim</b>，故此处按逗号再切一次并逐段归一）；</li>
     *   <li>丢弃空段与非已知角色名（{@link RoleEnum#isValid}）——非法项不视为任何角色，避免拼写漂移静默放行。</li>
     * </ul>
     * 返回空集合表示「任何角色均不得从 PC 管理端登录」（fail-closed）；配置键缺失时由
     * {@code AuthProperties} 的默认值 {@code ADMIN} 兜底，不会落到空集合。
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
                String candidate = normalizeRole(part);
                if (RoleEnum.isValid(candidate)) {
                    allowed.add(candidate);
                }
            }
        }
        return allowed;
    }

    /** 角色名归一：null 归一为 ""（空串非合法角色，天然被 {@link RoleEnum#isValid} 判否，避免对不可变集合传入 null） */
    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }
}
