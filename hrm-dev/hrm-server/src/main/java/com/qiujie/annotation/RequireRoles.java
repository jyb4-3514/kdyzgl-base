package com.qiujie.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点级角色白名单（对齐 Mock `route.roles`）。
 * <p>
 * 标注于方法或类；{@code RequireRolesInterceptor} 取「方法级优先、类级其次」，
 * 调用者角色（以 Redis 会话为准）不在白名单内 → HTTP 403 + code 403。
 * <p>
 * 为什么必须显式声明：Mock 在加载期强校验「鉴权路由必须有 roles」，漏声明即配置错误；
 * 服务端以「非公开端点未声明门槛 → 403」的 fail-closed 方式保证同一纪律，杜绝漏声明即静默放行。
 * 公开端点（{@code com.qiujie.common.PublicEndpoints}）无需声明。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRoles {

    /**
     * 允许访问的角色名，与 {@code UserContext.getRole()} 的字符串值逐一比对。
     * 表示「任意登录角色可访问」时写全三角色：{@code {"ADMIN","STATION_ADMIN","STAFF"}}。
     */
    String[] value();
}
