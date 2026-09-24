package com.qiujie.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADMIN 专属接口标记（可标注于方法或类）。
 *
 * @deprecated 已由 {@link RequireRoles} 统一表达，语义等价于 {@code @RequireRoles({"ADMIN"})}。
 * 本批已将既有 24 接口全部迁移为 {@code @RequireRoles}；保留本注解仅用于兼容外部/历史引用，
 * 新代码一律使用 {@link RequireRoles}（见架构 1.4.1「避免双注解长期并存」）。
 * TODO(扩展): 下一批次确认无引用后删除本注解及其在 RequireRolesInterceptor 中的兼容分支。
 */
@Deprecated(since = "24→145 接口 P0 地基批次", forRemoval = true)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
