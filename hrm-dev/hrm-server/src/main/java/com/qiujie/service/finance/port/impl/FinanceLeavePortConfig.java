package com.qiujie.service.finance.port.impl;

import com.qiujie.service.finance.port.ApprovedLeaveDaysPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 财务域跨域端口装配（已批请假天数，依赖倒置断环）。
 * <p>
 * 请假域（P7）提供真实的 {@link ApprovedLeaveDaysPort} 实现 Bean 后，本降级 Bean 自动不生效，
 * 无需改动财务域代码——与 P5 {@code HrPortConfig} 同一模式。
 */
@Configuration
public class FinanceLeavePortConfig {

    @Bean
    @ConditionalOnMissingBean(ApprovedLeaveDaysPort.class)
    public ApprovedLeaveDaysPort unavailableApprovedLeaveDaysPort() {
        return new UnavailableApprovedLeaveDaysPort();
    }
}
