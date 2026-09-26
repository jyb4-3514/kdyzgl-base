package com.qiujie.service.registration.impl;

import com.qiujie.config.RegistrationProperties;
import com.qiujie.dto.registration.RegistrationSubmitRequest;
import com.qiujie.entity.EmployeeRegistration;
import com.qiujie.entity.HrFlow;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeRegistrationMapper;
import com.qiujie.mapper.HrFlowMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.AuthService;
import com.qiujie.service.hr.HrFlowService;
import com.qiujie.service.registration.support.RegistrationConstants;
import com.qiujie.service.registration.support.RegistrationThrottle;
import com.qiujie.vo.registration.RegistrationDetailVO;
import com.qiujie.vo.registration.RegistrationSubmitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-2 提交 / R-3 详情 / M-8 清理 单测（B3）。
 * <p>覆盖：apply_no 唯一（两段式回填）、重复提交 9307、意向驿站 4001/4004、出参脱敏、
 * 惰性超时置终态 + 清凭据、留存期清理。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class RegistrationServiceImplTest {

    private final EmployeeRegistrationMapper registrationMapper = mock(EmployeeRegistrationMapper.class);
    private final HrFlowMapper hrFlowMapper = mock(HrFlowMapper.class);
    private final StationMapper stationMapper = mock(StationMapper.class);
    private final AuthService authService = mock(AuthService.class);
    private final HrFlowService hrFlowService = mock(HrFlowService.class);
    private final RegistrationThrottle throttle = mock(RegistrationThrottle.class);
    private final RegistrationProperties properties = new RegistrationProperties();

    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(registrationMapper, hrFlowMapper, stationMapper, authService,
                hrFlowService, properties, throttle);
        when(registrationMapper.insert(any(EmployeeRegistration.class))).thenAnswer(inv -> {
            EmployeeRegistration registration = inv.getArgument(0);
            registration.setId(1L);
            return 1;
        });
        when(stationMapper.selectById(anyLong())).thenReturn(enabledStation());
        when(hrFlowService.createSelfRegisterOnboarding(any(), any(), any(), any())).thenReturn(10L);
    }

    private Station enabledStation() {
        Station station = new Station();
        station.setId(3L);
        station.setStationName("城东驿站");
        station.setStatus(1);
        return station;
    }

    private RegistrationSubmitRequest validRequest() {
        RegistrationSubmitRequest request = new RegistrationSubmitRequest();
        request.setRealName("李四");
        request.setPhone("13912345678");
        request.setSmsCode("123456");
        request.setIntentStationId(3L);
        request.setIntentPosition("分拣员");
        request.setPassword("Init1234");
        request.setAgreementVersion("v1.0");
        return request;
    }

    private EmployeeRegistration submittedRegistration() {
        EmployeeRegistration registration = new EmployeeRegistration();
        registration.setId(1L);
        registration.setApplyNo("RG-20260926-0001");
        registration.setFlowId(10L);
        registration.setRealName("李四");
        registration.setPhone("13912345678");
        registration.setApplyStationId(3L);
        registration.setApplyPosition("分拣员");
        registration.setSource(RegistrationConstants.SOURCE_STAFF_H5);
        registration.setStatus(RegistrationConstants.STATUS_SUBMITTED);
        registration.setExpireTime(LocalDateTime.now().plusDays(7));
        registration.setPasswordHash("$2a$10$hash");
        return registration;
    }

    @Test
    @DisplayName("提交：建单 + 两段式编号回填 + 关联审批单 + 密码仅留痕（BCrypt）、query_token 不写")
    void submitCreatesRegistrationAndFlow() {
        when(registrationMapper.selectCount(any())).thenReturn(0L);

        RegistrationSubmitVO vo = service.submit(validRequest(), "1.2.3.4");

        assertEquals(RegistrationConstants.STATUS_SUBMITTED, vo.getStatus());
        assertTrue(vo.getApplyNo().matches("^RG-\\d{8}-0001$"), "apply_no 两段式回填：" + vo.getApplyNo());

        ArgumentCaptor<EmployeeRegistration> captor = ArgumentCaptor.forClass(EmployeeRegistration.class);
        verify(registrationMapper).insert(captor.capture());
        EmployeeRegistration saved = captor.getValue();
        assertEquals(10L, saved.getFlowId());
        assertEquals(RegistrationConstants.SOURCE_STAFF_H5, saved.getSource());
        assertTrue(saved.getPasswordHash().startsWith("$2"), "注册密码仅 BCrypt 留痕");
        assertNull(saved.getQueryTokenHash(), "query_token_hash 一期恒不写入");
        assertTrue(saved.getExpireTime() != null, "应写入过期基准（create + N 天）");
    }

    @Test
    @DisplayName("重复提交：同号已有 SUBMITTED → 9307（不建新单）")
    void duplicateSubmittedRejected() {
        when(registrationMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(validRequest(), null));
        assertEquals(ErrorCode.REGISTRATION_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已注册手机号：提交段不区分（无 9310/2003），仍正常受理（M-2 严格形态）")
    void alreadyRegisteredPhoneStillAcceptedSameShape() {
        // 同号历史仅有 REJECTED → selectCount(SUBMITTED)=0 → 不阻断；服务无「账号存在性」查询分支
        when(registrationMapper.selectCount(any())).thenReturn(0L);

        RegistrationSubmitVO vo = service.submit(validRequest(), null);

        assertEquals(RegistrationConstants.STATUS_SUBMITTED, vo.getStatus());
        assertTrue(vo.getApplyNo().startsWith("RG-"));
    }

    @Test
    @DisplayName("意向驿站不存在 → 4001；停用 → 4004")
    void intentStationValidation() {
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(stationMapper.selectById(anyLong())).thenReturn(null);
        BusinessException notFound = assertThrows(BusinessException.class, () -> service.submit(validRequest(), null));
        assertEquals(ErrorCode.STATION_NOT_FOUND.getCode(), notFound.getCode());

        Station disabled = enabledStation();
        disabled.setStatus(0);
        when(stationMapper.selectById(anyLong())).thenReturn(disabled);
        BusinessException disabledEx = assertThrows(BusinessException.class, () -> service.submit(validRequest(), null));
        assertEquals(ErrorCode.STATION_DISABLED.getCode(), disabledEx.getCode());
    }

    @Test
    @DisplayName("R-3 详情：手机号脱敏、含状态标签、不回传凭据")
    void detailMasksPhone() {
        when(registrationMapper.selectOne(any())).thenReturn(submittedRegistration());

        RegistrationDetailVO vo = service.detail("RG-20260926-0001");

        assertEquals("139****5678", vo.getPhone());
        assertEquals("审批中", vo.getStatusLabel());
        assertEquals("城东驿站", vo.getStationName());
    }

    @Test
    @DisplayName("R-3 详情：申请不存在 → 404")
    void detailNotFound() {
        when(registrationMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail("RG-X"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("惰性超时：查询已过期 SUBMITTED → 置 EXPIRED、清凭据、同步 hr_flow→REJECTED")
    void lazyExpireOnDetail() {
        EmployeeRegistration registration = submittedRegistration();
        registration.setExpireTime(LocalDateTime.now().minusDays(1));
        when(registrationMapper.selectOne(any())).thenReturn(registration);

        HrFlow flow = new HrFlow();
        flow.setId(10L);
        flow.setStatus("IN_PROGRESS");
        when(hrFlowMapper.selectByIdForUpdate(10L)).thenReturn(flow);

        RegistrationDetailVO vo = service.detail("RG-20260926-0001");

        assertEquals(RegistrationConstants.STATUS_EXPIRED, vo.getStatus());
        assertNull(registration.getPasswordHash(), "终态清密码散列");
        assertNull(registration.getQueryTokenHash());
        assertEquals("REJECTED", flow.getStatus(), "申请侧超时应同步流程侧终态");
        verify(hrFlowMapper).updateById(flow);
    }

    @Test
    @DisplayName("M-8 清理：超期未审转终态 + 超留存期终态移除")
    void cleanupExpiredHandlesBothBranches() {
        EmployeeRegistration overdue = submittedRegistration();
        overdue.setExpireTime(LocalDateTime.now().minusDays(1));
        when(hrFlowMapper.selectByIdForUpdate(10L)).thenReturn(new HrFlow());

        EmployeeRegistration stale = submittedRegistration();
        stale.setId(2L);
        stale.setStatus(RegistrationConstants.STATUS_REJECTED);

        when(registrationMapper.selectList(any())).thenReturn(List.of(overdue)).thenReturn(List.of(stale));

        int handled = service.cleanupExpired();

        assertEquals(2, handled);
        assertEquals(RegistrationConstants.STATUS_EXPIRED, overdue.getStatus());
        verify(registrationMapper).deleteById(2L);
    }
}
