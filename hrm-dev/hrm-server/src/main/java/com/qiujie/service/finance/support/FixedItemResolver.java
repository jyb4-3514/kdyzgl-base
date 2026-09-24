package com.qiujie.service.finance.support;

import com.qiujie.entity.HrAllowance;
import com.qiujie.entity.HrSalary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * FIXED 来源解析器：取人事定薪项（对齐 Mock {@code fixedAmount}）。
 * <p>
 * 支持的 {@code params}：
 * <ul>
 *   <li>{@code field}=basicSalary / postSalary / performanceBase → 直接取该项金额；</li>
 *   <li>{@code field}=allowances → 无 {@code allowanceKey} 取津贴合计；有 {@code allowanceKey} 取指定津贴项；</li>
 * </ul>
 * 无定薪档案 → 0 并写文案「未设置定薪档案，按 0 计」。
 */
@Component
public class FixedItemResolver implements PayrollItemResolver {

    @Override
    public String source() {
        return PayrollSource.FIXED.name();
    }

    @Override
    public PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemParamAccessor accessor = new PayrollItemParamAccessor(params);
        HrSalary salary = ctx == null ? null : ctx.salary();
        if (salary == null) {
            return PayrollItemAmount.zero("未设置定薪档案，按 0 计");
        }
        String field = accessor.string("field");
        if ("allowances".equals(field)) {
            return resolveAllowance(salary, accessor);
        }
        BigDecimal amount = PayrollSalaryField.value(salary, field);
        return new PayrollItemAmount(amount, "取人事定薪项：" + PayrollSalaryField.label(field));
    }

    /** 津贴项：无 key 取合计，有 key 取指定项（未命中 → 0，文案回落到 key） */
    private PayrollItemAmount resolveAllowance(HrSalary salary, PayrollItemParamAccessor accessor) {
        List<HrAllowance> allowances = salary.getAllowances() == null ? List.of() : salary.getAllowances();
        String allowanceKey = accessor.string("allowanceKey");
        if (allowanceKey == null || allowanceKey.isBlank()) {
            BigDecimal total = salary.getAllowancesTotal() == null ? BigDecimal.ZERO : salary.getAllowancesTotal();
            return new PayrollItemAmount(total, "取人事定薪项：津贴合计（" + allowances.size() + " 项）");
        }
        for (HrAllowance allowance : allowances) {
            if (allowance != null && allowanceKey.equals(allowance.getKey())) {
                BigDecimal amount = allowance.getAmount() == null ? BigDecimal.ZERO : allowance.getAmount();
                return new PayrollItemAmount(amount, "取人事定薪项：" + allowance.getName());
            }
        }
        return PayrollItemAmount.zero("取人事定薪项：" + allowanceKey);
    }
}
