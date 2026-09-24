package com.qiujie.service.hr.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 步骤「按序办理 + 状态守卫」纯逻辑单测（对齐 Mock {@code hrStore.completeStep} 判序与文案）。
 * <p>覆盖边界：空流程 / 跳步 / 重复办理 / 步骤不存在 / 流程非进行中。</p>
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrFlowStepGuardTest {

    private List<HrFlowStepGuard.StepState> onboardingSteps(int done) {
        return build(HrConstants.ONBOARDING_STEPS, done);
    }

    private List<HrFlowStepGuard.StepState> offboardingSteps(int done) {
        return build(HrConstants.OFFBOARDING_STEPS, done);
    }

    private List<HrFlowStepGuard.StepState> build(List<HrConstants.StepDef> defs, int done) {
        List<HrFlowStepGuard.StepState> list = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) {
            HrConstants.StepDef def = defs.get(i);
            list.add(new HrFlowStepGuard.StepState(def.key(), def.name(),
                    i < done ? HrConstants.STEP_STATUS_DONE : HrConstants.STEP_STATUS_PENDING));
        }
        return list;
    }

    @Test
    @DisplayName("空流程办理首个待办步骤 → 通过")
    void firstStepPasses() {
        assertNull(HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_IN_PROGRESS,
                onboardingSteps(0), "SUBMIT_MATERIALS"));
    }

    @Test
    @DisplayName("跳步（未办第 1 步直接办第 3 步）→ 拒绝")
    void skipStepRejected() {
        assertEquals("请先办理「提交资料」",
                HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_IN_PROGRESS,
                        onboardingSteps(0), "CREATE_ACCOUNT"));
    }

    @Test
    @DisplayName("重复办理已完成步骤 → 拒绝")
    void duplicateStepRejected() {
        assertEquals("「提交资料」已完成，不可重复办理",
                HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_IN_PROGRESS,
                        onboardingSteps(1), "SUBMIT_MATERIALS"));
    }

    @Test
    @DisplayName("办理不存在的步骤键 → 拒绝")
    void unknownStepRejected() {
        assertEquals("流程步骤不存在",
                HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_IN_PROGRESS,
                        onboardingSteps(0), "NOT_A_STEP"));
    }

    @Test
    @DisplayName("流程已驳回/已完成 → 不可继续办理")
    void notInProgressRejected() {
        assertEquals("流程已已驳回，不可继续办理",
                HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_REJECTED,
                        onboardingSteps(1), "HR_REVIEW"));
        assertEquals("流程已已完成，不可继续办理",
                HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_COMPLETED,
                        onboardingSteps(6), "DONE"));
    }

    @Test
    @DisplayName("离职步骤：空流程办理离岗 → 拒绝（须先办主管审批）")
    void offboardingLeaveFirstRejected() {
        assertEquals("请先办理「主管审批」",
                HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_IN_PROGRESS,
                        offboardingSteps(0), "LEAVE"));
    }

    @Test
    @DisplayName("离职步骤：办完前 5 步后办理离岗 → 通过")
    void offboardingLeaveAfterSettlementPasses() {
        assertNull(HrFlowStepGuard.checkOrder(HrConstants.FLOW_STATUS_IN_PROGRESS,
                offboardingSteps(5), "LEAVE"));
    }
}
