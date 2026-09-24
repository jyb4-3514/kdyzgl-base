package com.qiujie.annotation;

import com.qiujie.enums.DataScopePolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 路径资源归属（L3）逐端点声明（见架构 1.4.2 / ADR-07）。
 * <p>
 * L1（查询参数 stationId 静默收敛）与 L2（端点级角色 403）由统一机制全局处理；
 * L3 无法统一：是 404 还是 403 必须在端点上声明，否则会破坏 Mock 契约。
 * <p>
 * 使用约定：被标注方法的「资源归属驿站 id」由 {@link DataScopeStationId} 标注的 {@code Long} 入参提供，
 * 由 {@code DataScopeAspect} 在方法执行前调用 {@code ResourceAccessChecker} 校验：
 * <ul>
 *   <li>{@code SILENT}——不校验；</li>
 *   <li>{@code NOT_FOUND} / {@code FORBIDDEN}——ADMIN 放行，归属不符或资源无归属时按策略抛错。</li>
 * </ul>
 * TODO(扩展): 后续批次接入「按资源类型 + 路径变量 id 自动解析归属驿站」的解析器注册表，
 *   使端点无需手动传入归属 id（当前以显式入参标注，避免引入尚不存在的资源类型枚举）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 越权策略，缺省不校验（SILENT） */
    DataScopePolicy policy() default DataScopePolicy.SILENT;
}
