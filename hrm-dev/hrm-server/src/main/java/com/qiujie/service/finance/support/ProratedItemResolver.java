package com.qiujie.service.finance.support;

import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.HrSalary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PRORATED 来源解析器：定薪字段按出勤班次折算（S2b，方案 §4 候选 A）。
 * <p>
 * 公式：{@code amount = 定薪字段 × 实出班次 ÷ 应出班次}（HALF_UP 到分）；应出班次为 0 时按
 * {@code hrm.algo.payroll.zeroSchedulePolicy} 兜底（默认 {@code FULL_BASIC}，不因数据缺失克扣）。
 * <p>
 * 支持的 {@code params}：{@code field}（默认 {@code basicSalary}，仅限 {@code hrm.algo.payroll.proratedFields} 内的字段）。
 * <p>
 * 为什么只折算「折算字段清单」内的字段：折算范围属计薪口径（T2 已裁定只折算基本工资），
 * 用配置清单做护栏，规则项若误配岗位/津贴折算则按原值返回并在明细注明，避免静默越权改口径。
 */
@Component
@RequiredArgsConstructor
public class ProratedItemResolver implements PayrollItemResolver {

    /** 默认折算字段（规则项未声明 field 时） */
    private static final String DEFAULT_FIELD = "basicSalary";

    private final AlgoProperties algoProperties;

    @Override
    public String source() {
        return PayrollSource.PRORATED.name();
    }

    @Override
    public PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemParamAccessor accessor = new PayrollItemParamAccessor(params);
        HrSalary salary = ctx == null ? null : ctx.salary();
        if (salary == null) {
            return PayrollItemAmount.zero("未设置定薪档案，按 0 计");
        }
        String field = accessor.string("field");
        if (field == null || field.isBlank()) {
            field = DEFAULT_FIELD;
        }
        BigDecimal base = PayrollSalaryField.value(salary, field);
        String label = PayrollSalaryField.label(field);

        if (!isProratedField(field)) {
            // 护栏：字段不在折算清单内 → 按原值返回，不擅自折算（口径边界）
            return new PayrollItemAmount(base, "取人事定薪项：" + label + "（未在折算字段清单内，按原值）");
        }

        AttendanceStat attendance = ctx.attendance();
        if (attendance == null) {
            // 无考勤统计：与「无排班」同义 → 走兜底，不克扣
            return zeroScheduleResult(base, label);
        }
        int required = attendance.requiredShifts();
        int attended = attendance.attendedShifts();
        if (required <= 0) {
            return zeroScheduleResult(base, label);
        }
        BigDecimal amount = ShiftPayrollPolicy.prorated(base, attended, required,
                algoProperties.getPayroll().getZeroSchedulePolicy());
        String detail = label + " " + PayrollNumberFormat.plain(base) + " × "
                + attended + "/" + required + " = " + PayrollNumberFormat.plain(amount) + " 元";
        return new PayrollItemAmount(amount, detail);
    }

    /** 应出班次为 0（或考勤缺失）的兜底：按 zeroSchedulePolicy 计，明细注明「按全额」或「按 0」 */
    private PayrollItemAmount zeroScheduleResult(BigDecimal base, String label) {
        BigDecimal amount = ShiftPayrollPolicy.prorated(base, 0, 0,
                algoProperties.getPayroll().getZeroSchedulePolicy());
        String reason = ShiftPayrollPolicy.ZERO_SCHEDULE_FULL_BASIC
                .equalsIgnoreCase(algoProperties.getPayroll().getZeroSchedulePolicy())
                ? "应出班次为 0，按全额计" : "应出班次为 0，按 0 计";
        return new PayrollItemAmount(amount, label + " " + PayrollNumberFormat.plain(amount) + " 元（" + reason + "）");
    }

    /** 字段是否在 {@code hrm.algo.payroll.proratedFields} 清单内（默认 [basicSalary]） */
    private boolean isProratedField(String field) {
        List<String> fields = algoProperties.getPayroll().getProratedFields();
        return fields != null && fields.contains(field);
    }
}
