package com.qiujie.service.workorder.support;

import com.qiujie.enums.RoleEnum;

/**
 * 工单越权与可及性判定（纯逻辑，对齐 Mock {@code routes/workOrder.js} 的 {@code canView/canAssign/canManage}）。
 * <p>
 * 为什么独立成纯函数：越权口径是审计项，散在 Service 的 if 链里漏判即静默放行；抽出后可逐条单测覆盖
 * （见 {@code WorkOrderAccessPolicyTest}），与 P7 {@code LeaveAccessPolicy} 同法。
 * <p>
 * 本类只回答「是否允许」，不含错误码与文案：404 / 8002 / 8003 由 Service 按端点逐条给出（架构 §6.2 P8：
 * 「越权口径按端点逐端点实现，不得统一」）。
 */
public final class WorkOrderAccessPolicy {

    private WorkOrderAccessPolicy() {
    }

    /** 是否超级管理员 */
    public static boolean isAdmin(String role) {
        return RoleEnum.isAdmin(role);
    }

    /** 是否站长 */
    public static boolean isStationAdmin(String role) {
        return RoleEnum.STATION_ADMIN.name().equals(role);
    }

    /**
     * 详情可见范围（Mock {@code detail}）：ADMIN 全量；站长/员工须与工单同站（跨站按 404 返回，隐藏存在性）。
     */
    public static boolean canView(String role, Long userStationId, Long orderStationId) {
        return isAdmin(role) || sameStation(userStationId, orderStationId);
    }

    /**
     * 流转权限（Mock {@code canManage}）：ADMIN / 本站站长 / 当前处理人本人。
     * 用于 {@code PUT /{id}/status} 与 {@code POST /{id}/transfer}（越权分别回 8002 / 8003）。
     */
    public static boolean canManage(String role, Long userStationId, Long userId,
                                    Long orderStationId, Long assigneeId) {
        if (isAdmin(role)) {
            return true;
        }
        if (isStationAdmin(role)) {
            return sameStation(userStationId, orderStationId);
        }
        // 普通员工：仅当本人是当前处理人
        return userId != null && userId.equals(assigneeId);
    }

    /**
     * 指派权限（Mock {@code canAssign}）：仅 ADMIN / 本站站长；普通员工不可指派（越权回 8002）。
     */
    public static boolean canAssign(String role, Long userStationId, Long orderStationId) {
        if (isAdmin(role)) {
            return true;
        }
        return isStationAdmin(role) && sameStation(userStationId, orderStationId);
    }

    /**
     * 转单对象同站约束：ADMIN 可跨站调人；站长与处理人只能在本站内消化（跨站回 8004）。
     * 「不能转给自己」由调用方按操作人判定（见 Service）。
     */
    public static boolean canTransferTo(String role, Long targetStationId, Long orderStationId) {
        return isAdmin(role) || sameStation(targetStationId, orderStationId);
    }

    /** 同一驿站（null 安全；任一端为 null 视为不匹配） */
    private static boolean sameStation(Long left, Long right) {
        return left != null && left.equals(right);
    }
}
