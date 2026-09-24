package com.qiujie.service.leave.support;

import com.qiujie.enums.RoleEnum;

/**
 * 请假越权与动作可及性判定（纯逻辑，对齐 Mock {@code leaveStore.toLeaveVO} 的派生标志与各写操作的守卫）。
 * <p>
 * 为什么独立成纯函数：越权判定是审计口径（谁在什么状态能看/能办），散落在 Service 的 if 链里
 * 一旦漏判即静默放行；抽成无副作用静态方法后可逐条单测覆盖（见 {@code LeaveAccessPolicyTest}），
 * 与 P6 {@code PayrollStateMachine} / P0 {@code RoleEnum} 同法。
 * <p>
 * 说明：本类只回答「是否允许」，不含错误码与文案；文案（9605/9607 等）由 Service 按 Mock 逐条给出。
 */
public final class LeaveAccessPolicy {

    private LeaveAccessPolicy() {
    }

    /** 是否超级管理员 */
    public static boolean isAdmin(String role) {
        return RoleEnum.ADMIN.name().equals(role);
    }

    /** 是否本站站长 */
    public static boolean isStationAdmin(String role) {
        return RoleEnum.STATION_ADMIN.name().equals(role);
    }

    /** 提交/重提前置：ADMIN 无上级可审不可提交；无归属驿站不可提交（对齐 Mock {@code applicantGuard}） */
    public static boolean canSubmit(String role, Long stationId) {
        return !isAdmin(role) && stationId != null;
    }

    /**
     * 详情可见范围：ADMIN 全量 / 本人 / 本站站长（对齐 Mock {@code detail}）。
     * 不可见时 Service 抛 9605（不暴露其他驿站数据的存在性）。
     */
    public static boolean canView(String role, Long userId, Long ownerId, Long leaveStationId, Long userStationId) {
        if (isAdmin(role)) {
            return true;
        }
        if (userId != null && userId.equals(ownerId)) {
            return true;
        }
        return isStationAdmin(role) && userStationId != null && userStationId.equals(leaveStationId);
    }

    /** 可编辑：仅申请本人且处于待初审（状态合法性由状态机矩阵判定，避免双来源） */
    public static boolean canEdit(Long userId, Long ownerId, String status) {
        return owns(userId, ownerId) && allows(LeaveStateMachine.Action.UPDATE, status);
    }

    /** 可撤销：申请人且处于两种待审态 */
    public static boolean canCancel(Long userId, Long ownerId, String status) {
        return owns(userId, ownerId) && allows(LeaveStateMachine.Action.CANCEL, status);
    }

    /** 可撤回（出参派生标志）：ADMIN 且已通过（对齐 Mock {@code canRevoke}） */
    public static boolean canRevoke(String role, String status) {
        return isAdmin(role) && allows(LeaveStateMachine.Action.REVOKE, status);
    }

    /**
     * 站长初审可办：限本驿站站长、不能审自己；状态须为待初审（跨站/审自己 → 9605，状态非法 → 9602）。
     */
    public static boolean canStationApprove(Long operatorStationId, Long leaveStationId,
                                            Long operatorId, Long ownerId, String status) {
        if (operatorStationId == null || !operatorStationId.equals(leaveStationId)) {
            return false;
        }
        if (operatorId != null && operatorId.equals(ownerId)) {
            return false;
        }
        return allows(LeaveStateMachine.Action.STATION_APPROVE, status);
    }

    /** 老板终审可办：不能审自己；状态须为待终审（对齐 Mock finalApprove） */
    public static boolean canFinalApprove(Long operatorId, Long ownerId, String status) {
        if (operatorId != null && operatorId.equals(ownerId)) {
            return false;
        }
        return allows(LeaveStateMachine.Action.FINAL_APPROVE, status);
    }

    /** 是否同一人（null 安全；任一端为 null 视为不匹配） */
    public static boolean owns(Long userId, Long ownerId) {
        return userId != null && userId.equals(ownerId);
    }

    /** 状态合法性统一委托状态机矩阵（状态名 → 枚举，未知状态视为不允许） */
    private static boolean allows(LeaveStateMachine.Action action, String status) {
        return action.allows(LeaveStateMachine.Status.of(status));
    }
}
