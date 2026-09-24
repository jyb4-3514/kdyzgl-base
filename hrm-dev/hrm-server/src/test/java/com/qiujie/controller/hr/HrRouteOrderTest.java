package com.qiujie.controller.hr;

import com.qiujie.common.PageResult;
import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.hr.HrFlowService;
import com.qiujie.service.hr.HrProfileService;
import com.qiujie.vo.hr.HrFlowVO;
import com.qiujie.vo.hr.HrProfileDetailVO;
import com.qiujie.vo.hr.HrProfileVO;
import com.qiujie.vo.hr.HrSalaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 人事路由回归：静态段（{@code /hr/profiles}、{@code /hr/onboarding}）与变量段（{@code /{employeeId}}、{@code /{id}}）
 * 段数不同，不会互相遮蔽；{@code /{id}/steps/{key}/complete} 亦可达。
 * <p>用 standalone MockMvc（不依赖 DB/Redis/容器）；注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段。</p>
 */
class HrRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HrProfileService profileService = mock(HrProfileService.class);
        HrFlowService flowService = mock(HrFlowService.class);

        when(profileService.listProfiles(any())).thenReturn(PageResult.of(0, 1, 10, List.<HrProfileVO>of()));
        when(profileService.listSalaries(any())).thenReturn(PageResult.of(0, 1, 10, List.<HrSalaryVO>of()));
        HrProfileDetailVO profile = new HrProfileDetailVO();
        profile.setEmployeeId(1L);
        when(profileService.profileDetail(anyLong())).thenReturn(profile);

        when(flowService.listOnboardings(any())).thenReturn(PageResult.of(0, 1, 10, List.<HrFlowVO>of()));
        when(flowService.listOffboardings(any())).thenReturn(PageResult.of(0, 1, 10, List.<HrFlowVO>of()));
        HrFlowVO flow = new HrFlowVO();
        flow.setId(1L);
        when(flowService.onboardingDetail(anyLong())).thenReturn(flow);
        when(flowService.offboardingDetail(anyLong())).thenReturn(flow);
        when(flowService.completeOnboardingStep(anyLong(), any(), any())).thenReturn(flow);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HrProfileController(profileService), new HrFlowController(flowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("静态段 /hr/profiles 可达")
    void profileListReachable() throws Exception {
        mockMvc.perform(get("/api/v1/hr/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("变量段 /hr/profiles/{employeeId} 可达")
    void profileDetailReachable() throws Exception {
        mockMvc.perform(get("/api/v1/hr/profiles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(1));
    }

    @Test
    @DisplayName("静态段 /hr/onboarding 可达")
    void onboardingListReachable() throws Exception {
        mockMvc.perform(get("/api/v1/hr/onboarding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("变量段 /hr/onboarding/{id} 可达")
    void onboardingDetailReachable() throws Exception {
        mockMvc.perform(get("/api/v1/hr/onboarding/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("/hr/onboarding/{id}/steps/{key}/complete 可达（段数更多，不与 /{id} 冲突）")
    void onboardingStepCompleteReachable() throws Exception {
        mockMvc.perform(post("/api/v1/hr/onboarding/1/steps/CREATE_ACCOUNT/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("/hr/offboarding/{id} 与 /hr/offboarding 均可达")
    void offboardingReachable() throws Exception {
        mockMvc.perform(get("/api/v1/hr/offboarding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/v1/hr/offboarding/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
