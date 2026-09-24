package com.qiujie.service.hr.support;

import com.qiujie.dto.hr.HrProfileUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 人事档案字段校验纯逻辑单测（对齐 Mock {@code validateProfile}）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrProfileValidatorTest {

    private HrProfileUpdateRequest base() {
        HrProfileUpdateRequest body = new HrProfileUpdateRequest();
        body.setEducation("BACHELOR");
        body.setContractType("FIXED_TERM");
        body.setContractStart("2026-01-01");
        body.setContractEnd("2029-01-01");
        body.setProbationMonths(3);
        return body;
    }

    @Test
    @DisplayName("空载荷 → 通过（未传字段不校验）")
    void emptyPayloadPasses() {
        assertNull(HrProfileValidator.validate(new HrProfileUpdateRequest()));
        assertNull(HrProfileValidator.validate(null));
    }

    @Test
    @DisplayName("合法载荷 → 通过")
    void validPayloadPasses() {
        assertNull(HrProfileValidator.validate(base()));
    }

    @Test
    @DisplayName("学历/合同类型非法 → 拒绝（文案按 Mock validateProfile 拼合法枚举键，非中文标签）")
    void invalidDictionaryRejected() {
        HrProfileUpdateRequest body = base();
        body.setEducation("PHD");
        // 契约依据：Mock routes/hr.js:74 `学历仅支持 ${EDUCATION_KEYS.join(' / ')}`，
        // EDUCATION_KEYS = Object.keys(EDUCATION_LABEL) = MASTER/BACHELOR/COLLEGE/HIGH_SCHOOL（枚举键，非标签）
        assertEquals("学历仅支持 MASTER / BACHELOR / COLLEGE / HIGH_SCHOOL", HrProfileValidator.validate(body));

        HrProfileUpdateRequest body2 = base();
        body2.setContractType("UNKNOWN");
        // 契约依据：Mock routes/hr.js:76 `合同类型仅支持 ${CONTRACT_TYPE_KEYS.join(' / ')}`
        assertEquals("合同类型仅支持 FIXED_TERM / NON_FIXED_TERM / INTERN / DISPATCH",
                HrProfileValidator.validate(body2));
    }

    @Test
    @DisplayName("合同到期早于生效日 → 拒绝")
    void contractEndBeforeStartRejected() {
        HrProfileUpdateRequest body = base();
        body.setContractStart("2026-06-01");
        body.setContractEnd("2026-01-01");
        assertEquals("合同到期日不能早于生效日", HrProfileValidator.validate(body));
    }

    @Test
    @DisplayName("日期格式非法 → 拒绝")
    void invalidDateRejected() {
        HrProfileUpdateRequest body = base();
        body.setRegularDate("2026/01/01");
        assertEquals("regularDate 格式须为 YYYY-MM-DD", HrProfileValidator.validate(body));
    }

    @Test
    @DisplayName("试用期月数越界 → 拒绝；边界 0/12 → 通过")
    void probationMonthsBounds() {
        HrProfileUpdateRequest body = base();
        body.setProbationMonths(13);
        assertEquals("试用期月数须为 0-12 的整数", HrProfileValidator.validate(body));

        body.setProbationMonths(0);
        assertNull(HrProfileValidator.validate(body));
        body.setProbationMonths(12);
        assertNull(HrProfileValidator.validate(body));
    }

    @Test
    @DisplayName("社保基数/银行卡/紧急联系人 → 边界校验")
    void moneyAndSensitiveFields() {
        HrProfileUpdateRequest body = base();
        body.setSocialSecurityBase(new BigDecimal("-1"));
        assertEquals("社保基数须不小于 0", HrProfileValidator.validate(body));

        HrProfileUpdateRequest body2 = base();
        body2.setBankAccount("1234");
        assertEquals("银行卡号须为 12-25 位数字", HrProfileValidator.validate(body2));

        HrProfileUpdateRequest body3 = base();
        body3.setEmergencyContactPhone("12345");
        assertEquals("紧急联系人手机号格式不正确", HrProfileValidator.validate(body3));

        HrProfileUpdateRequest body4 = base();
        body4.setEmergencyContactName("王");
        assertEquals("紧急联系人姓名长度须为 2-20", HrProfileValidator.validate(body4));
    }
}
