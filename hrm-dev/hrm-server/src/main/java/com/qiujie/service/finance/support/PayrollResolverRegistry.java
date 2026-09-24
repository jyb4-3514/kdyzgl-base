package com.qiujie.service.finance.support;

import com.qiujie.config.AlgoProperties;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计薪项来源解析器注册表（算法 S2 核心）。
 * <p>
 * 构造时收集容器内全部 {@link PayrollItemResolver}，以 {@code Map<source, resolver>} 注册；
 * 算薪时按 {@code source} 一次分发，<b>新增来源不改本类</b>（开闭原则）。
 * <p>
 * 未知来源处理由 {@code hrm.algo.payroll.unknownSourcePolicy} 决定：
 * <ul>
 *   <li>{@code ZERO}（默认）：按 0 计并记 warn，不中断整批（防一条脏配置打挂整批算薪）；</li>
 *   <li>{@code THROW}：抛业务异常（严格模式，供运维显式开启）。</li>
 * </ul>
 */
@Slf4j
@Component
public class PayrollResolverRegistry {

    /** 未知来源严格模式开关值 */
    private static final String POLICY_THROW = "THROW";

    private final Map<String, PayrollItemResolver> resolvers;
    private final String unknownSourcePolicy;

    public PayrollResolverRegistry(List<PayrollItemResolver> resolverList, AlgoProperties algoProperties) {
        Map<String, PayrollItemResolver> registered = new LinkedHashMap<>();
        if (resolverList != null) {
            for (PayrollItemResolver resolver : resolverList) {
                // 同 source 重复注册：后者覆盖前者并告警，避免静默歧义
                if (registered.put(resolver.source(), resolver) != null) {
                    log.warn("计薪项来源解析器重复注册，后注册者生效：source={}", resolver.source());
                }
            }
        }
        this.resolvers = Collections.unmodifiableMap(registered);
        this.unknownSourcePolicy = algoProperties.getPayroll().getUnknownSourcePolicy();
        log.info("计薪项来源解析器注册完成：sources={}", registered.keySet());
    }

    /**
     * 按来源解析单项。
     *
     * @param source 来源（FIXED/ATTENDANCE/KPI/MANUAL，或任意未知值）
     * @param params 规则项参数
     * @param ctx    算薪上下文
     */
    public PayrollItemAmount resolve(String source, Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemResolver resolver = resolvers.get(source);
        if (resolver != null) {
            return resolver.resolve(params, ctx);
        }
        if (POLICY_THROW.equalsIgnoreCase(unknownSourcePolicy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未知计薪来源：" + source);
        }
        log.warn("计薪规则项来源未知，按 0 计：source={}", source);
        return new PayrollItemAmount(BigDecimal.ZERO, "未知来源，按 0 计");
    }

    /** 已注册的来源集合（供自检/日志） */
    public Set<String> registeredSources() {
        return resolvers.keySet();
    }
}
