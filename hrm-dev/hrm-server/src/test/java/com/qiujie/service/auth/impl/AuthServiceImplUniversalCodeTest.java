package com.qiujie.service.auth.impl;

import com.qiujie.config.AuthProperties;
import com.qiujie.config.SmsProperties;
import com.qiujie.dto.auth.SmsLoginRequest;
import com.qiujie.entity.Employee;
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
import com.qiujie.service.support.sms.SmsSender;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.SessionUtil;
import com.qiujie.vo.auth.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测试环境万能验证码的<b>服务层短路</b>行为单测（Mockito，无需 DB / Redis / Spring 容器）。
 * <p>覆盖「生效时是否消耗尝试计数」：万能码放行时必须<b>不触碰</b> {@link SmsCodeStore}
 * （不取码、不读计数、不自增、不作废），但仍完成会话签发（证明未绕过登录链路）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AuthServiceImplUniversalCodeTest {

    private static final String PHONE = "13800000000";

    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final SmsCodeStore smsCodeStore = mock(SmsCodeStore.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    private SmsProperties smsProperties;
    private MockEnvironment environment;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        smsProperties = new SmsProperties();
        environment = new MockEnvironment(); // 无 active profile = 非生产
        service = new AuthServiceImpl(
                employeeMapper,
                mock(LoginLogMapper.class),
                mock(DepartmentMapper.class),
                mock(StationMapper.class),
                mock(BCryptPasswordEncoder.class),
                jwtUtil,
                mock(SessionUtil.class),
                new AuthProperties(),
                smsProperties,
                mock(SmsSender.class),
                smsCodeStore,
                mock(DeviceTicketStore.class),
                mock(TrustedDeviceRegistry.class),
                mock(DeviceFingerprint.class),
                mock(CaptchaStore.class),
                environment);
    }

    private Employee enabledEmployee() {
        Employee e = new Employee();
        e.setId(5L);
        e.setUsername("staff");
        e.setRole("STAFF");
        e.setStatus(1);
        e.setPhone(PHONE);
        return e;
    }

    private SmsLoginRequest request(String code) {
        SmsLoginRequest request = new SmsLoginRequest();
        request.setPhone(PHONE);
        request.setCode(code);
        // 旧端 H5（无 as）派生员工端（端准入默认允许 STAFF），保证端准入稳定通过
        request.setClientType("H5");
        return request;
    }

    @Test
    @DisplayName("万能码生效：直接通过并完成会话签发，全程不读取/不消耗验证码与尝试计数")
    void universalCodeBypassesStore() {
        smsProperties.setDevUniversalCode("000000");
        when(employeeMapper.selectOne(any())).thenReturn(enabledEmployee());

        LoginVO vo = service.smsLogin(request("000000"), AuthRequestContext.empty(), null);

        assertNotNull(vo, "万能码放行后仍应完成会话签发（未绕过登录链路）");
        verify(jwtUtil).generate(any(), any(), any(), any());
        // 不消耗：存储层零交互
        verify(smsCodeStore, never()).getCode(any(), any());
        verify(smsCodeStore, never()).attempts(any(), any());
        verify(smsCodeStore, never()).incrementAttempts(any(), any());
        verify(smsCodeStore, never()).clearCode(any(), any());
    }

    @Test
    @DisplayName("配置为空：万能码不生效，仍按原逻辑取码（无有效码 → 1102，且不产生失败计数）")
    void disabledUniversalCodeFallsThrough() {
        when(employeeMapper.selectOne(any())).thenReturn(enabledEmployee());
        when(smsCodeStore.getCode(SmsScene.LOGIN, PHONE)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.smsLogin(request("000000"), AuthRequestContext.empty(), null));

        verify(smsCodeStore).getCode(SmsScene.LOGIN, PHONE);
        verify(smsCodeStore, never()).incrementAttempts(any(), any());
    }

    @Test
    @DisplayName("配置非空但 code 不相等：不生效，仍按原逻辑取码（1102）")
    void mismatchFallsThrough() {
        smsProperties.setDevUniversalCode("000000");
        when(employeeMapper.selectOne(any())).thenReturn(enabledEmployee());
        when(smsCodeStore.getCode(SmsScene.LOGIN, PHONE)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.smsLogin(request("111111"), AuthRequestContext.empty(), null));

        verify(smsCodeStore).getCode(SmsScene.LOGIN, PHONE);
    }

    @Test
    @DisplayName("生产环境：即使配置了万能码也不短路（服务层双保险，仍取码 → 1102）")
    void productionNeverBypasses() {
        smsProperties.setDevUniversalCode("000000");
        environment.setActiveProfiles("prod");
        when(employeeMapper.selectOne(any())).thenReturn(enabledEmployee());
        when(smsCodeStore.getCode(SmsScene.LOGIN, PHONE)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.smsLogin(request("000000"), AuthRequestContext.empty(), null));

        // 生产不短路：仍访问存储层取码
        verify(smsCodeStore).getCode(SmsScene.LOGIN, PHONE);
    }
}
