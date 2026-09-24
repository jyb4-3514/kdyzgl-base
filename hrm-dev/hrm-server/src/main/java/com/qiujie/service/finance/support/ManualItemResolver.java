package com.qiujie.service.finance.support;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MANUAL 来源解析器：人工填写项（对齐 Mock 分支）。
 * <p>
 * 取 {@code params.defaultValue} 作为初始金额（缺失或非数字 → 0）；草稿/已驳回状态下可被
 * {@code PUT /finance/payrolls/{id}/items} 调整。文案固定「人工填写项，草稿状态下可调整」。
 */
@Component
public class ManualItemResolver implements PayrollItemResolver {

    @Override
    public String source() {
        return PayrollSource.MANUAL.name();
    }

    @Override
    public PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemParamAccessor accessor = new PayrollItemParamAccessor(params);
        return new PayrollItemAmount(accessor.numberOrZero("defaultValue"), "人工填写项，草稿状态下可调整");
    }
}
