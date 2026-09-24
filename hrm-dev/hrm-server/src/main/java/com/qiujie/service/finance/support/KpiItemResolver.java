package com.qiujie.service.finance.support;

import com.qiujie.config.AlgoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * KPI 来源解析器（对齐 Mock {@code kpiAmount}）。
 * <p>
 * 公式：{@code amount = round(绩效基数 × clamp(KPI得分/100, 0, capRatio))}。
 * <ul>
 *   <li>无 KPI 评分记录 → 0，文案「该月无 KPI 评分记录，按 0 计」；</li>
 *   <li>绩效基数为 0（或无定薪档案）→ 0，文案「定薪档案缺少绩效基数，按 0 计」；</li>
 *   <li>{@code capRatio} 未显式配置时取 {@code hrm.algo.payroll.defaultCapRatio}（默认 1.0）；
 *       显式配置但 {@code <=0} 或非数字 → 视作 1（对齐 Mock {@code capRatio>0 ? capRatio : 1}）。</li>
 * </ul>
 * <b>Q3 未裁定</b>：{@code capRatio} 上限保持可配（种子 1.2 来自规则数据，不写死在代码）。
 */
@Component
@RequiredArgsConstructor
public class KpiItemResolver implements PayrollItemResolver {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** KPI 得分/百分比换算的中间精度（最后一步才取整，避免累积误差） */
    private static final int RATE_SCALE = 10;

    private final AlgoProperties algoProperties;

    @Override
    public String source() {
        return PayrollSource.KPI.name();
    }

    @Override
    public PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemParamAccessor accessor = new PayrollItemParamAccessor(params);
        String baseField = accessor.string("baseField");
        if (baseField == null || baseField.isBlank()) {
            baseField = "performanceBase";
        }
        BigDecimal base = ctx == null ? BigDecimal.ZERO : PayrollSalaryField.value(ctx.salary(), baseField);

        if (ctx == null || ctx.kpiScore() == null) {
            return PayrollItemAmount.zero("该月无 KPI 评分记录，按 0 计");
        }
        if (base.signum() == 0) {
            return PayrollItemAmount.zero("定薪档案缺少绩效基数，按 0 计");
        }

        BigDecimal rawCapRatio = accessor.has("capRatio")
                ? accessor.number("capRatio")
                : BigDecimal.valueOf(algoProperties.getPayroll().getDefaultCapRatio());
        boolean capRatioUsable = rawCapRatio != null && rawCapRatio.signum() > 0;
        BigDecimal effectiveCapRatio = capRatioUsable ? rawCapRatio : BigDecimal.ONE;

        BigDecimal rate = ctx.kpiScore().divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP);
        rate = rate.max(BigDecimal.ZERO).min(effectiveCapRatio);
        BigDecimal amount = base.multiply(rate).setScale(0, RoundingMode.HALF_UP);

        String capText = capRatioUsable && rawCapRatio.compareTo(BigDecimal.ONE) != 0
                ? "（上限 " + Math.round(rawCapRatio.doubleValue() * 100) + "%）" : "";
        String detail = "绩效基数 " + PayrollNumberFormat.plain(base)
                + " × KPI 得分 " + PayrollNumberFormat.plain(ctx.kpiScore()) + "%" + capText
                + " = " + PayrollNumberFormat.plain(amount) + " 元";
        return new PayrollItemAmount(amount, detail);
    }
}
