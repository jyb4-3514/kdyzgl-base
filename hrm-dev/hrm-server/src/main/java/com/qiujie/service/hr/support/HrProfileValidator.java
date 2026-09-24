package com.qiujie.service.hr.support;

import com.qiujie.dto.hr.HrProfileUpdateRequest;

import java.math.BigDecimal;

/**
 * 人事档案字段校验（纯逻辑，对齐 Mock {@code validateProfile}）。
 * <p>
 * 日期用「起止先后」而非只校验格式，避免落下合同到期早于生效的档案。
 * 返回错误文案（null=通过），由 Service 包装为 400。
 */
public final class HrProfileValidator {

    private HrProfileValidator() {
    }

    public static String validate(HrProfileUpdateRequest body) {
        if (body == null) {
            return null;
        }
        if (body.getEducation() != null && !HrConstants.educationKeys().contains(body.getEducation())) {
            return "学历仅支持 " + String.join(" / ", HrConstants.educationKeys());
        }
        if (body.getContractType() != null && !HrConstants.contractTypeKeys().contains(body.getContractType())) {
            return "合同类型仅支持 " + String.join(" / ", HrConstants.contractTypeKeys());
        }
        for (String[] pair : new String[][]{
                {"contractStart", body.getContractStart()},
                {"contractEnd", body.getContractEnd()},
                {"probationEnd", body.getProbationEnd()},
                {"regularDate", body.getRegularDate()}}) {
            if (!HrValidateSupport.isBlank(pair[1]) && !HrValidateSupport.isDate(pair[1])) {
                return pair[0] + " 格式须为 YYYY-MM-DD";
            }
        }
        String start = HrValidateSupport.isBlank(body.getContractStart()) ? null : body.getContractStart();
        String end = HrValidateSupport.isBlank(body.getContractEnd()) ? null : body.getContractEnd();
        if (start != null && end != null && end.compareTo(start) < 0) {
            return "合同到期日不能早于生效日";
        }
        Integer probationMonths = body.getProbationMonths();
        if (probationMonths != null && (probationMonths < 0 || probationMonths > 12)) {
            return "试用期月数须为 0-12 的整数";
        }
        BigDecimal socialSecurityBase = body.getSocialSecurityBase();
        if (socialSecurityBase != null && socialSecurityBase.compareTo(BigDecimal.ZERO) < 0) {
            return "社保基数须不小于 0";
        }
        if (!HrValidateSupport.isBlank(body.getEmergencyContactPhone())
                && !HrValidateSupport.isPhone(body.getEmergencyContactPhone())) {
            return "紧急联系人手机号格式不正确";
        }
        if (!HrValidateSupport.isBlank(body.getEmergencyContactName())
                && !HrValidateSupport.textLen(body.getEmergencyContactName(), 2, 20)) {
            return "紧急联系人姓名长度须为 2-20";
        }
        if (!HrValidateSupport.isBlank(body.getBankName())
                && !HrValidateSupport.textLen(body.getBankName(), 2, 50)) {
            return "开户行长度须为 2-50";
        }
        if (!HrValidateSupport.isBlank(body.getBankAccount())
                && !body.getBankAccount().replaceAll("\\s", "").matches("\\d{12,25}")) {
            return "银行卡号须为 12-25 位数字";
        }
        return null;
    }
}
