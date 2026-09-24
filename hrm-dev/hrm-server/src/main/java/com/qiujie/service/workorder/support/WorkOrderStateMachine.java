package com.qiujie.service.workorder.support;

import java.util.Map;
import java.util.Set;

/**
 * 工单流转状态机（对齐 Mock {@code routes/workOrder.js} 的 {@code TRANSITIONS}）。
 * <p>
 * 迁移矩阵 {@code {0:[1,3], 1:[2], 2:[1,3], 3:[]}}：
 * 待处理可接单/直关；处理中只能解决；已解决可驳回重开或关闭；已关闭为终态。
 * <p>
 * 为什么表驱动：状态 × 目标的合法组合是审计口径，一张矩阵可一眼核对；散落 if 链一旦漏判即静默放行非法流转。
 * 与 P6 {@code PayrollStateMachine} / P7 {@code LeaveStateMachine} 同法。
 */
public final class WorkOrderStateMachine {

    private WorkOrderStateMachine() {
    }

    /** 迁移矩阵：当前状态 → 允许抵达的目标状态集合（不可变） */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            WorkOrderConstants.STATUS_PENDING, Set.of(WorkOrderConstants.STATUS_PROCESSING, WorkOrderConstants.STATUS_CLOSED),
            WorkOrderConstants.STATUS_PROCESSING, Set.of(WorkOrderConstants.STATUS_RESOLVED),
            WorkOrderConstants.STATUS_RESOLVED, Set.of(WorkOrderConstants.STATUS_PROCESSING, WorkOrderConstants.STATUS_CLOSED),
            WorkOrderConstants.STATUS_CLOSED, Set.of());

    /** 当前状态允许抵达的目标状态集合（未知状态一律空集 = 不可流转） */
    public static Set<Integer> targets(int from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    /** 是否为合法流转 */
    public static boolean canTransition(int from, int to) {
        return targets(from).contains(to);
    }

    /** 是否终态（已解决 / 已关闭） */
    public static boolean isTerminal(int status) {
        return WorkOrderConstants.TERMINAL_STATUSES.contains(status);
    }

    /** 是否「未处理完」（待处理 / 处理中），超 SLA 判定参与集合 */
    public static boolean isOpen(int status) {
        return WorkOrderConstants.OPEN_STATUSES.contains(status);
    }

    /**
     * 目标状态对应的留痕动作（严格对齐 Mock 三元表达式）：
     * 目标 3→close、2→resolve、1→accept、其余→reopen。
     * <p>
     * 说明：按本矩阵，目标 1 是「处理中」，从 0（接单）与 2（驳回重开）都会写入 {@code accept}——
     * 与 Mock 完全一致；{@code reopen} 分支在 Mock 中因目标值域恒为 {1,2,3} 而不可达，此处保留以逐字等价。
     */
    public static String actionOf(int target) {
        if (target == WorkOrderConstants.STATUS_CLOSED) {
            return WorkOrderConstants.ACTION_CLOSE;
        }
        if (target == WorkOrderConstants.STATUS_RESOLVED) {
            return WorkOrderConstants.ACTION_RESOLVE;
        }
        if (target == WorkOrderConstants.STATUS_PROCESSING) {
            return WorkOrderConstants.ACTION_ACCEPT;
        }
        return WorkOrderConstants.ACTION_REOPEN;
    }
}
