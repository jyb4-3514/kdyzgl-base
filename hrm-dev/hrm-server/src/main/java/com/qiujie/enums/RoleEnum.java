package com.qiujie.enums;

import java.util.Arrays;

/**
 * 角色枚举（对齐 db.md 3.3 与 Mock `constants/role.js`）：
 * <ul>
 *   <li>{@code ADMIN}=超级管理员：唯一不受数据范围收敛、可跨驿站筛选的角色；</li>
 *   <li>{@code STATION_ADMIN}=站长：数据范围限本人驿站；</li>
 *   <li>{@code STAFF}=员工：数据范围限本人驿站 / 本人数据。</li>
 * </ul>
 * 为什么用枚举收敛角色：角色名散落为字符串字面量时拼写漂移会导致越权判定静默失效（放行）。
 * 注意：注解 {@code @RequireRoles} 取值必须是编译期常量，故控制器注解仍写字符串字面量（与 Mock `roles` 同形），
 * 本枚举承担逻辑判定与合法性校验。
 */
public enum RoleEnum {

    /** 超级管理员 */
    ADMIN,
    /** 站长 */
    STATION_ADMIN,
    /** 员工 */
    STAFF;

    /** 全局管理员判定（对齐 Mock `isAdmin`）：唯一享有跨驿站全量可见范围的角色 */
    public static boolean isAdmin(String role) {
        return ADMIN.name().equals(role);
    }

    /** 是否为本系统已知角色；未知角色一律按最小权限处理（不视为任何角色的成员） */
    public static boolean isValid(String role) {
        return role != null && Arrays.stream(values()).anyMatch(r -> r.name().equals(role));
    }
}
