package com.qiujie.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「资源归属驿站 id」入参（配合 {@link DataScope} 使用，须为 {@code Long} 类型）。
 * <p>
 * 由 {@code DataScopeAspect} 读取并交由 {@code ResourceAccessChecker} 校验归属。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScopeStationId {
}
