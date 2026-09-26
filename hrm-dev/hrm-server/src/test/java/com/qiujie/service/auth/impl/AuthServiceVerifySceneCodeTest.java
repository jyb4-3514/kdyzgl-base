package com.qiujie.service.auth.impl;

import com.qiujie.config.AuthProperties;
import com.qiujie.config.SmsProperties;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.LoginLogMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.support.CaptchaStore;
import com.qiujie.service.auth.support.DeviceFingerprint;
import com.qiujie.service.auth.support.DeviceTicketStore;
import com.qiujie.service.auth.support.SmsCodeStore;
import com.qiujie.service.auth.support.TrustedDeviceRegistry;
import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsSender;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.SessionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册场景验证码「校验 + 一次性作废」与重放拒绝单测（B3 幂等/重放守卫）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AuthServiceVerifySceneCodeTest {

    private static final String PHONE = "13800000000";
    private static final String CODE = "123456";

    private final SmsCodeStore smsCodeStore = mock(SmsCodeStore.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                mock(EmployeeMapper.class), mock(LoginLogMapper.class), mock(DepartmentMapper.class),
                mock(StationMapper.class), mock(BCryptPasswordEncoder.class), mock(JwtUtil.class),
                mock(SessionUtil.class), new AuthProperties(), new SmsProperties(), mock(SmsSender.class),
                smsCodeStore, mock(DeviceTicketStore.class), mock(TrustedDeviceRegistry.class),
                mock(DeviceFingerprint.class), mock(CaptchaStore.class), new MockEnvironment());
    }

    @Test
    @DisplayName("REGISTER 码校验通过即一次性作废；重放同一码 → 1102")
    void registerCodeConsumedOnceThenReplayRejected() {
        when(smsCodeStore.getCode(SmsScene.REGISTER, PHONE)).thenReturn(CODE);
        service.verifySceneCode(SmsScene.REGISTER, PHONE, CODE);
        verify(smsCodeStore).clearCode(SmsScene.REGISTER, PHONE);

        // 重放：验证码已在首次校验时删除
        when(smsCodeStore.getCode(SmsScene.REGISTER, PHONE)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifySceneCode(SmsScene.REGISTER, PHONE, CODE));
        assertEquals(ErrorCode.SMS_CODE_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("未申请/已过期 → 1102；尝试超限 → 1103")
    void invalidAndOverLimitCodes() {
        when(smsCodeStore.getCode(SmsScene.REGISTER, PHONE)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifySceneCode(SmsScene.REGISTER, PHONE, CODE));
        assertEquals(ErrorCode.SMS_CODE_INVALID.getCode(), ex.getCode());

        when(smsCodeStore.getCode(SmsScene.REGISTER, PHONE)).thenReturn(CODE);
        when(smsCodeStore.attempts(SmsScene.REGISTER, PHONE)).thenReturn(5);
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> service.verifySceneCode(SmsScene.REGISTER, PHONE, "000000"));
        assertEquals(ErrorCode.SMS_CODE_ATTEMPTS_EXCEEDED.getCode(), ex2.getCode());
    }
}
