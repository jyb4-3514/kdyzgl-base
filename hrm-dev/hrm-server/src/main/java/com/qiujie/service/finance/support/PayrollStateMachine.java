package com.qiujie.service.finance.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工资单状态机（纯逻辑，与 Mock {@code PAYROLL_ACTIONS} 逐条对齐）。
 * <p>
 * 流转：
 * <pre>
 * DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──publish──▶ PUBLISHED ──confirm──▶ CONFIRMED
 *                        └──reject──▶ REJECTED ──submit──▶ PENDING_APPROVAL
 * PUBLISHED ──objection──▶ PENDING_APPROVAL（回到审核，清空确认/发布时间）
 * </pre>
 * 为什么把动作矩阵收在一处：状态守卫若每个动作各写一遍判断，六态 × 六动作会形成 36 处口径，
 * 早晚漂移；集中后「当前状态允许哪些动作」只有一份真源，出参与写操作共用。
 */
public final class PayrollStateMachine {

    /** 动作名（与 Mock 出参 {@code actions} 的小写字符串一致，前端据此渲染按钮） */
    public static final String ACTION_SUBMIT = "submit";
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_OBJECTION = "objection";

    /** 状态 → 允许动作（顺序即出参顺序，与 Mock 逐条一致） */
    private static final Map<String, List<String>> ACTIONS = buildActions();

    private PayrollStateMachine() {
    }

    private static Map<String, List<String>> buildActions() {
        Map<String, List<String>> actions = new LinkedHashMap<>();
        actions.put(PayrollStatus.DRAFT.name(), List.of(ACTION_SUBMIT));
        actions.put(PayrollStatus.REJECTED.name(), List.of(ACTION_SUBMIT));
        actions.put(PayrollStatus.PENDING_APPROVAL.name(), List.of(ACTION_APPROVE, ACTION_REJECT));
        actions.put(PayrollStatus.APPROVED.name(), List.of(ACTION_PUBLISH));
        actions.put(PayrollStatus.PUBLISHED.name(), List.of(ACTION_CONFIRM, ACTION_OBJECTION));
        actions.put(PayrollStatus.CONFIRMED.name(), List.of());
        return actions;
    }

    /** 当前状态允许的动作（未知状态 → 空列表，等价于「任何动作都不允许」） */
    public static List<String> actionsOf(String status) {
        return ACTIONS.getOrDefault(status, List.of());
    }

    /** 当前状态是否允许该动作 */
    public static boolean allows(String status, String action) {
        return actionsOf(status).contains(action);
    }

    /**
     * 是否「可编辑」：可改 MANUAL 项金额、可被 generate 覆盖重建。
     * 仅 DRAFT / REJECTED（REJECTED 视同可编辑草稿，不必额外多一个状态）。
     */
    public static boolean isEditable(String status) {
        return PayrollStatus.DRAFT.name().equals(status) || PayrollStatus.REJECTED.name().equals(status);
    }
}
