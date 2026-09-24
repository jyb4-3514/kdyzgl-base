package com.qiujie.service.leave.support;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 请假 6 态状态机流转矩阵（对齐 Mock {@code leaveStore.js} 的 T1–T9 迁移路径）。
 * <p>
 * 为什么用枚举矩阵而非 if-else 链：状态 × 动作的合法组合是审计口径（谁在什么状态能做什么），
 * 表驱动可一眼核对，避免散落判断出现「某状态被漏判即放行」的静默越权（与 P6 {@code PayrollStateMachine} 同法）。
 */
public final class LeaveStateMachine {

    private LeaveStateMachine() {
    }

    /** 状态流转动作（提交/编辑/重提/撤销/两审各通过或驳回/撤回） */
    public enum Action {
        /** 提交申请（初始态由「是否有可用站长」决定，见 {@link #initialStatus}） */
        SUBMIT(EnumSet.noneOf(Status.class), null),
        /** 编辑（仅申请人本人在待初审，状态不变；非法回 9607） */
        UPDATE(EnumSet.of(Status.PENDING_STATION), Status.PENDING_STATION),
        /** 修改重提（原单 REJECTED 只读，另生成新单） */
        RESUBMIT(EnumSet.of(Status.REJECTED), null),
        /** 申请人撤销（两种待审态） */
        CANCEL(EnumSet.of(Status.PENDING_STATION, Status.PENDING_BOSS), Status.CANCELLED),
        /** 站长初审通过 */
        STATION_APPROVE(EnumSet.of(Status.PENDING_STATION), Status.PENDING_BOSS),
        /** 站长初审驳回 */
        STATION_REJECT(EnumSet.of(Status.PENDING_STATION), Status.REJECTED),
        /** 老板终审通过（落计薪天数快照） */
        FINAL_APPROVE(EnumSet.of(Status.PENDING_BOSS), Status.APPROVED),
        /** 老板终审驳回 */
        FINAL_REJECT(EnumSet.of(Status.PENDING_BOSS), Status.REJECTED),
        /** 审批人撤回已批单（仅 ADMIN + 账期锁校验） */
        REVOKE(EnumSet.of(Status.APPROVED), Status.REVOKED);

        private final Set<Status> allowedFrom;
        private final Status target;

        Action(Set<Status> allowedFrom, Status target) {
            this.allowedFrom = allowedFrom;
            this.target = target;
        }

        /** 该动作是否允许从给定状态发起 */
        public boolean allows(Status from) {
            return from != null && allowedFrom.contains(from);
        }

        /** 该动作的目标状态（SUBMIT/RESUBMIT 的初始态另定，返回 null） */
        public Status target() {
            return target;
        }
    }

    /** 请假状态枚举（与 {@link LeaveConstants} 字符串一一对应） */
    public enum Status {
        PENDING_STATION(LeaveConstants.STATUS_PENDING_STATION),
        PENDING_BOSS(LeaveConstants.STATUS_PENDING_BOSS),
        APPROVED(LeaveConstants.STATUS_APPROVED),
        REJECTED(LeaveConstants.STATUS_REJECTED),
        CANCELLED(LeaveConstants.STATUS_CANCELLED),
        REVOKED(LeaveConstants.STATUS_REVOKED);

        private final String code;

        Status(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** 字符串 → 枚举；未知道值返回 null（调用方按最小权限/非法处理） */
        public static Status of(String code) {
            return Arrays.stream(values())
                    .filter(status -> status.code.equals(code))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * 提交后的初始状态（T1 + Q4 降级）：
     * STAFF 且本站有可用站长 → 待初审；站长本人提交 或 无可用站长 → 待终审。
     */
    public static Status initialStatus(boolean submitterIsStationAdmin, boolean stationApproverAvailable) {
        return (submitterIsStationAdmin || !stationApproverAvailable) ? Status.PENDING_BOSS : Status.PENDING_STATION;
    }

    /** 审批动作的目标状态：通过 / 驳回 决定终态或下一审级 */
    public static Status approveTarget(Action action, boolean approved) {
        switch (action) {
            case STATION_APPROVE:
            case STATION_REJECT:
                return approved ? Status.PENDING_BOSS : Status.REJECTED;
            case FINAL_APPROVE:
            case FINAL_REJECT:
                return approved ? Status.APPROVED : Status.REJECTED;
            default:
                throw new IllegalArgumentException("非审批动作：" + action);
        }
    }

    /** 驳回阶段（仅驳回动作有值，通过返回 null） */
    public static String rejectStage(Action action, boolean approved) {
        if (approved) {
            return null;
        }
        return action == Action.STATION_REJECT ? LeaveConstants.STAGE_STATION : LeaveConstants.STAGE_BOSS;
    }
}
