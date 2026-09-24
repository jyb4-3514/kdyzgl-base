package com.qiujie.enums;

import java.util.Arrays;

/**
 * 客户端端类型（{@code X-Client-Type} 请求头，架构 multi-client-architecture §2.1.3）。
 * <p>
 * <b>安全定位（必读）</b>：端类型是<b>客户端自称</b>的元数据，<b>不可信、不参与任何鉴权/授权判定</b>。
 * 权限恒以服务端持有的会话角色（{@code LoginUser.role}，源自 Redis 会话）为唯一权威，
 * 故「低权限端伪造高权限端标识」无法获得任何额外权限（满足 M1 安全要求「端维度角色约束」）。
 * 本枚举仅用于 <b>审计 / 会话维度 / 限流</b> 与脏值归一，未知取值一律回落 {@link #WEB}，防止任意串进入会话存储。
 * <p>
 * TODO(扩展): 端 → 允许角色集合的显式约束（如员工端禁管理端点）属 M5 三端拆分，届时在
 *   {@code RequireRolesInterceptor} 之外增加「端维度」校验；本批不引入端维度鉴权，避免越权面扩大。
 */
public enum ClientType {

    /** PC 管理端（hrm-admin） */
    ADMIN,
    /** 老板端 */
    BOSS,
    /** 员工端（驿站助手） */
    STAFF,
    /** 网页端 / 缺省 */
    WEB;

    /** 缺省端类型（请求头缺失或取值非法时回落，与后端「同源网页」默认场景一致） */
    public static final ClientType DEFAULT = WEB;

    /** 归一化：忽略大小写与前后空白；未知/空值回落 {@link #DEFAULT} */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT.name();
        }
        String upper = value.trim().toUpperCase();
        return Arrays.stream(values()).anyMatch(t -> t.name().equals(upper)) ? upper : DEFAULT.name();
    }

    /** 是否为已知端类型（大小写不敏感） */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String upper = value.trim().toUpperCase();
        return Arrays.stream(values()).anyMatch(t -> t.name().equals(upper));
    }
}
