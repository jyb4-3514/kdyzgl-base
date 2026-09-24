package com.qiujie.service.hr.support;

import com.qiujie.dto.hr.SalaryAllowanceItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * 定薪字段校验（纯逻辑，对齐 Mock {@code validateSalary}）。
 * <p>
 * 金额项与津贴全是钱，负值一律挡在入口；生效日期决定留痕排序，必须是合法日期。
 * {@code isCreate=true}（保存定薪）时三项金额必填；{@code isCreate=false}（步骤办理）时仅校验显式传入项。
 * 返回错误文案（null=通过）。
 */
public final class HrSalaryValidator {

    private HrSalaryValidator() {
    }

    public static String validate(BigDecimal basicSalary, BigDecimal postSalary, BigDecimal performanceBase,
                                  List<SalaryAllowanceItem> allowances, String effectiveDate,
                                  String reason, boolean isCreate) {
        if (isCreate || basicSalary != null) {
            if (basicSalary == null || basicSalary.compareTo(BigDecimal.ZERO) < 0) {
                return "basicSalary 须为不小于 0 的数字";
            }
        }
        if (isCreate || postSalary != null) {
            if (postSalary == null || postSalary.compareTo(BigDecimal.ZERO) < 0) {
                return "postSalary 须为不小于 0 的数字";
            }
        }
        if (isCreate || performanceBase != null) {
            if (performanceBase == null || performanceBase.compareTo(BigDecimal.ZERO) < 0) {
                return "performanceBase 须为不小于 0 的数字";
            }
        }
        if (allowances != null) {
            for (SalaryAllowanceItem item : allowances) {
                if (item == null || !HrValidateSupport.textLen(item.getName(), 1, 20)) {
                    return "津贴项名称长度须为 1-20";
                }
                if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                    return "津贴金额须为不小于 0 的数字";
                }
            }
        }
        if (!HrValidateSupport.isBlank(effectiveDate) && !HrValidateSupport.isDate(effectiveDate)) {
            return "effectiveDate 格式须为 YYYY-MM-DD";
        }
        if (reason != null && !HrValidateSupport.isBlank(reason) && !HrValidateSupport.textLen(reason, 2, 50)) {
            return "调薪原因长度须为 2-50";
        }
        return null;
    }
}
