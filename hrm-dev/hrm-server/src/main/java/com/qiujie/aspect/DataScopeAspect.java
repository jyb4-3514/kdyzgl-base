package com.qiujie.aspect;

import com.qiujie.annotation.DataScope;
import com.qiujie.annotation.DataScopeStationId;
import com.qiujie.enums.DataScopePolicy;
import com.qiujie.service.support.ResourceAccessChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

/**
 * L3 路径资源越权切面：消费 {@link DataScope} 声明，调用 {@link ResourceAccessChecker} 校验归属。
 * <p>
 * 契约：被 {@code @DataScope(policy != SILENT)} 标注的方法必须有一个 {@link DataScopeStationId} 标注的
 * {@code Long} 入参（资源归属驿站 id）；缺失时按配置错误快速失败（与 Mock 加载期强校验同一纪律，不静默放行）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final ResourceAccessChecker resourceAccessChecker;

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        if (dataScope.policy() != DataScopePolicy.SILENT) {
            resourceAccessChecker.check(resolveResourceStationId(joinPoint), dataScope.policy());
        }
        return joinPoint.proceed();
    }

    /** 读取 {@link DataScopeStationId} 标注的入参作为资源归属驿站 id */
    private Long resolveResourceStationId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof DataScopeStationId) {
                    Object value = args[i];
                    if (value == null) {
                        return null;
                    }
                    if (value instanceof Long longValue) {
                        return longValue;
                    }
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    throw new IllegalStateException(
                            "@DataScopeStationId 入参必须为 Long 类型：" + signature.getName());
                }
            }
        }
        throw new IllegalStateException(
                "@DataScope(policy != SILENT) 方法缺少 @DataScopeStationId 入参（资源归属驿站 id）："
                        + signature.getName());
    }
}
