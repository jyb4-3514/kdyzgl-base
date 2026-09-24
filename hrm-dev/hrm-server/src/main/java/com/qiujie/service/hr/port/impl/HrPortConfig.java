package com.qiujie.service.hr.port.impl;

import com.qiujie.service.hr.port.PayrollSettlementPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 人事域跨域端口装配（ADR-03 断环）。
 * <p>
 * 为什么用 {@code @ConditionalOnMissingBean} 注册降级 Bean：财务域（P6）就绪后会提供真实的
 * {@link PayrollSettlementPort} 实现 Bean，届时本降级 Bean 自动不生效，无需改动人事域代码——
 * 这正是「依赖倒置 + 条件装配」相对直接依赖他域 Service 的价值所在。
 */
@Configuration
public class HrPortConfig {

    @Bean
    @ConditionalOnMissingBean(PayrollSettlementPort.class)
    public PayrollSettlementPort unavailablePayrollSettlementPort() {
        return new UnavailablePayrollSettlementPort();
    }
}
