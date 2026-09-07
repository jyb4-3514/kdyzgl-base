package com.qiujie.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADMIN 专属接口标记（可标注于方法或类）：
 * 由 RequireAdminInterceptor 拦截，非 ADMIN 角色访问返回 HTTP 403 + code 403。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
