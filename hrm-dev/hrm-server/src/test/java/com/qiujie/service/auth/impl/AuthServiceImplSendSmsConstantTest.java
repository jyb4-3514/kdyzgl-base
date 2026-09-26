package com.qiujie.service.auth.impl;

import com.qiujie.config.AuthProperties;
import com.qiujie.config.SmsProperties;
import com.qiujie.dto.auth.SmsSendRequest;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.LoginLogMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.support.AuthRequestContext;
import com.qiujie.service.auth.support.CaptchaStore;
import com.qiujie.service.auth.support.DeviceFingerprint;
import com.qiujie.service.auth.support.DeviceTicketStore;
import com.qiujie.service.auth.support.SmsCodeStore;
import com.qiujie.service.auth.support.TrustedDeviceRegistry;
import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsSendResult;
import com.qiujie.service.support.sms.SmsSender;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.SessionUtil;
import com.qiujie.vo.auth.SmsSendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-1 发码恒定化与场景分派单测（B3，M-1/M-2/SEC-FULL-08/18）。
 * <p>覆盖：已注册/未注册响应逐字段一致（消除 1109）、未知非空 scene 拒（非回落 LOGIN）、
 * REGISTER 场景不查账号存在性、空白 scene 仍回落 LOGIN。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AuthServiceImplSendSmsConstantTest {

    private static final String PHONE = "13800000000";

    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final SmsCodeStore smsCodeStore = mock(SmsCodeStore.class);
    private final SmsSender smsSender = mock(SmsSender.class);
    private final CaptchaStore captchaStore = mock(CaptchaStore.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                employeeMapper, mock(LoginLogMapper.class), mock(DepartmentMapper.class), mock(StationMapper.class),
                mock(BCryptPasswordEncoder.class), mock(JwtUtil.class), mock(SessionUtil.class), new AuthProperties(),
                new SmsProperties(), smsSender, smsCodeStore, mock(DeviceTicketStore.class),
                mock(TrustedDeviceRegistry.class), mock(DeviceFingerprint.class), captchaStore, new MockEnvironment());
        when(smsSender.send(any(), any(), any())).thenReturn(SmsSendResult.ok("logging"));
        when(smsCodeStore.codeExpireInSeconds()).thenReturn(300);
        when(smsCodeStore.nextAllowedInSeconds()).thenReturn(60);
        when(captchaStore.isEnabled()).thenReturn(false);
    }

    private Employee enabledEmployee() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setStatus(1);
        employee.setPhone(PHONE);
        return employee;
    }

    private SmsSendRequest loginRequest() {
        SmsSendRequest request = new SmsSendRequest();
        request.setPhone(PHONE);
        return request;
    }

    @Test
    @DisplayName("发码恒定化：已注册（启用）与未注册号码响应逐字段一致，未注册静默成功（不真发码）")
    void responseIsIdenticalForRegisteredAndUnregistered() {
        when(employeeMapper.selectOne(any())).thenReturn(enabledEmployee());
        SmsSendVO registered = service.sendSms(loginRequest(), AuthRequestContext.empty());

        // 未注册：仍返回同构受理外观，且不得调用短信通道（静默成功）
        when(employeeMapper.selectOne(any())).thenReturn(null);
        SmsSendVO unregistered = service.sendSms(loginRequest(), AuthRequestContext.empty());

        assertEquals(registered.getSent(), unregistered.getSent());
        assertEquals(registered.getExpireIn(), unregistered.getExpireIn());
        assertEquals(registered.getNextAllowedIn(), unregistered.getNextAllowedIn());
        assertEquals(registered.getRequireCaptcha(), unregistered.getRequireCaptcha());
        assertEquals(Boolean.TRUE, unregistered.getSent());
        // 全程仅注册号码触达通道一次；未注册号码静默成功（不真发码）
        verify(smsSender, times(1)).send(eq(PHONE), eq(SmsScene.LOGIN), anyString());
    }

    @Test
    @DisplayName("M-1/SEC-FULL-18：未知非空 scene → 400「不支持的短信场景」（不回落 LOGIN）")
    void unknownNonBlankSceneRejected() {
        SmsSendRequest request = new SmsSendRequest();
        request.setScene("BOGUS");
        request.setPhone(PHONE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendSms(request, AuthRequestContext.empty()));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("不支持的短信场景", ex.getMessage());
        verify(smsCodeStore, never()).saveCode(any(), any(), anyString());
    }

    @Test
    @DisplayName("缺省/空白 scene 仍回落 LOGIN（保既有前端契约）")
    void blankSceneFallsBackToLogin() {
        when(employeeMapper.selectOne(any())).thenReturn(enabledEmployee());
        SmsSendVO vo = service.sendSms(loginRequest(), AuthRequestContext.empty());

        assertNotNull(vo);
        verify(smsCodeStore).saveCode(eq(SmsScene.LOGIN), eq(PHONE), anyString());
    }

    @Test
    @DisplayName("REGISTER 场景：不查账号存在性，未注册号码亦可发码（M-1）")
    void registerSceneNeverQueriesAccount() {
        SmsSendRequest request = new SmsSendRequest();
        request.setScene("REGISTER");
        request.setPhone(PHONE);

        SmsSendVO vo = service.sendSms(request, AuthRequestContext.empty());

        assertNotNull(vo);
        verify(employeeMapper, never()).selectOne(any());
        verify(smsCodeStore).saveCode(eq(SmsScene.REGISTER), eq(PHONE), anyString());
    }
}
