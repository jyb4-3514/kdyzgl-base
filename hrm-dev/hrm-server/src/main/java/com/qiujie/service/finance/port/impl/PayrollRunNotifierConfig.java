package com.qiujie.service.finance.port.impl;

import com.qiujie.service.finance.port.PayrollRunNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 算薪通知端口装配：无真实实现时提供空实现兜底。
 * <p>
 * 与 {@code FinanceLeavePortConfig} 同一手法——用 {@code @ConditionalOnMissingBean} 保证
 * 「B4a 提供真实 Bean 即自动取代空实现」，B3 无需改代码即可切换。
 */
@Configuration
public class PayrollRunNotifierConfig {

    @Bean
    @ConditionalOnMissingBean(PayrollRunNotifier.class)
    public PayrollRunNotifier noopPayrollRunNotifier() {
        return new NoopPayrollRunNotifier();
    }
}
