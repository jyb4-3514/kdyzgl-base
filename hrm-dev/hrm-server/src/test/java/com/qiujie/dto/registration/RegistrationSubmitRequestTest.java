package com.qiujie.dto.registration;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-2 入参「字段白名单 + 未知字段拒绝 + @Valid 约束」单测（M-3/S-3，§11.5 表 A）。
 * <p>未知字段由 DTO 的 {@code @JsonAnySetter} 兜底抛错，Jackson 包装为 {@link JsonMappingException}
 * （Spring 侧经 GlobalExceptionHandler 统一转 HTTP 400）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class RegistrationSubmitRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("夹带 role（审批侧字段）→ 反序列化失败（未知字段拒绝）")
    void smuggledRoleRejected() {
        String json = "{\"realName\":\"李四\",\"phone\":\"13912345678\",\"smsCode\":\"123456\","
                + "\"intentStationId\":3,\"agreementVersion\":\"v1.0\",\"role\":\"STATION_ADMIN\"}";
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue(json, RegistrationSubmitRequest.class));
    }

    @Test
    @DisplayName("夹带 basicSalary / deptId / username（审批侧字段）→ 400")
    void smuggledSalaryAndDeptRejected() {
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("{\"realName\":\"李四\",\"phone\":\"13912345678\",\"smsCode\":\"123456\","
                        + "\"intentStationId\":3,\"agreementVersion\":\"v1.0\",\"basicSalary\":1000}",
                        RegistrationSubmitRequest.class));
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("{\"realName\":\"李四\",\"phone\":\"13912345678\",\"smsCode\":\"123456\","
                        + "\"intentStationId\":3,\"agreementVersion\":\"v1.0\",\"deptId\":2}",
                        RegistrationSubmitRequest.class));
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("{\"realName\":\"李四\",\"phone\":\"13912345678\",\"smsCode\":\"123456\","
                        + "\"intentStationId\":3,\"agreementVersion\":\"v1.0\",\"username\":\"hacker\"}",
                        RegistrationSubmitRequest.class));
    }

    @Test
    @DisplayName("白名单 7 字段可反序列化；password 缺省为 null（选填）")
    void whitelistDeserializes() throws Exception {
        String json = "{\"realName\":\"李四\",\"phone\":\"13912345678\",\"smsCode\":\"123456\","
                + "\"intentStationId\":3,\"intentPosition\":\"分拣员\",\"agreementVersion\":\"v1.0\"}";
        RegistrationSubmitRequest request = mapper.readValue(json, RegistrationSubmitRequest.class);
        assertEquals(3L, request.getIntentStationId());
        assertEquals("分拣员", request.getIntentPosition());
        assertNull(request.getPassword());
    }

    @Test
    @DisplayName("@Valid：缺必填项（姓名/手机号/验证码/意向驿站/条款版本）→ 校验不通过")
    void beanValidationRejectsMissingRequired() {
        Set<ConstraintViolation<RegistrationSubmitRequest>> violations = validator.validate(new RegistrationSubmitRequest());
        assertFalse(violations.isEmpty());
        // 姓名、手机号、验证码、意向驿站、条款版本五个必填各至少一条
        assertTrue(violations.size() >= 5);
    }

    @Test
    @DisplayName("@Valid：手机号非法、姓名超长、密码弱（非空时）→ 校验不通过")
    void beanValidationRejectsInvalidValues() {
        RegistrationSubmitRequest request = new RegistrationSubmitRequest();
        request.setRealName("甲");
        request.setPhone("12345");
        request.setSmsCode("12");
        request.setIntentStationId(3L);
        request.setAgreementVersion("v1.0");
        request.setPassword("abc");
        Set<ConstraintViolation<RegistrationSubmitRequest>> violations = validator.validate(request);
        assertTrue(violations.size() >= 4);
    }

    @Test
    @DisplayName("@Valid：合规入参（password 为空）→ 校验通过")
    void beanValidationAcceptsValidPayload() {
        RegistrationSubmitRequest request = new RegistrationSubmitRequest();
        request.setRealName("李四");
        request.setPhone("13912345678");
        request.setSmsCode("123456");
        request.setIntentStationId(3L);
        request.setAgreementVersion("v1.0");
        assertTrue(validator.validate(request).isEmpty());
    }
}
