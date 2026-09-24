package com.qiujie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 人事业务默认值（{@code hrm.hr.*}）。
 * <p>
 * 为什么外置：试用期默认月数、默认合同期限属「可调业务默认值」，代码内联即形成不可审计的魔法数
 * （规则 §11.4、反模式 A03）。键名与默认值取自 Mock {@code hrStore.createEmployeeForFlow}
 * （试用期默认 3 个月、合同默认 3 个月），上线即无行为变化。
 * <p>
 * 计费/提成/SLA/考核口径类参数不在此类（属口径红线，须用户裁定），本类仅承载与口径无关的建档默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.hr")
public class HrProperties {

    /** 入职建档默认试用期（月），对齐 Mock 建档默认 3 */
    private int defaultProbationMonths = 3;

    /** 入职建档默认合同期限（月），对齐 Mock 建档默认 +3 月 */
    private int defaultContractMonths = 3;
}
