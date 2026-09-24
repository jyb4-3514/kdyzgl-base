package com.qiujie.service.hr.support;

import java.util.List;

/**
 * 流程「按序办理 + 状态守卫」纯逻辑（入职与离职共用，避免两套流程各写一遍流转判断）。
 * <p>
 * 逐条对齐 Mock {@code hrStore.completeStep} 的判序与文案：
 * <ol>
 *   <li>流程非进行中 → 「流程已{状态}，不可继续办理」；</li>
 *   <li>步骤键不存在 → 「流程步骤不存在」；</li>
 *   <li>步骤已完成 → 「「{步骤名}」已完成，不可重复办理」；</li>
 *   <li>非当前待办步骤 → 「请先办理「{当前步骤名}」」（<b>跳步必须拒绝</b>，不得放宽）；</li>
 * </ol>
 * 「流程不存在」由调用方按端点语义处理（入职/离职分别 404），不在本类内。
 * <p>
 * 设计为纯函数：入参只含状态快照，返回错误文案（null=通过），便于离线单测覆盖空流程/跳步/重复办理等边界。
 */
public final class HrFlowStepGuard {

    private HrFlowStepGuard() {
    }

    /** 步骤状态快照（Service 由 {@code hr_flow_step} 记录转换而来） */
    public record StepState(String key, String name, String status) {
    }

    /**
     * 校验步骤可否办理。
     *
     * @param flowStatus  流程状态（IN_PROGRESS/COMPLETED/REJECTED）
     * @param steps       步骤快照（须按 step_order 升序）
     * @param targetKey   目标步骤键
     * @return 错误文案；{@code null} 表示通过
     */
    public static String checkOrder(String flowStatus, List<StepState> steps, String targetKey) {
        if (!HrConstants.FLOW_STATUS_IN_PROGRESS.equals(flowStatus)) {
            return "流程已" + nullToEmpty(HrConstants.flowStatusLabel(flowStatus)) + "，不可继续办理";
        }
        StepState step = find(steps, targetKey);
        if (step == null) {
            return "流程步骤不存在";
        }
        if (HrConstants.STEP_STATUS_DONE.equals(step.status())) {
            return "「" + step.name() + "」已完成，不可重复办理";
        }
        StepState current = firstPending(steps);
        if (current == null || !current.key().equals(targetKey)) {
            return "请先办理「" + (current == null ? "" : current.name()) + "」";
        }
        return null;
    }

    /** 首个待办步骤（无则返回 null，表示全流程已办完） */
    public static StepState firstPending(List<StepState> steps) {
        if (steps == null) {
            return null;
        }
        for (StepState step : steps) {
            if (HrConstants.STEP_STATUS_PENDING.equals(step.status())) {
                return step;
            }
        }
        return null;
    }

    private static StepState find(List<StepState> steps, String key) {
        if (steps == null || key == null) {
            return null;
        }
        for (StepState step : steps) {
            if (key.equals(step.key())) {
                return step;
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
