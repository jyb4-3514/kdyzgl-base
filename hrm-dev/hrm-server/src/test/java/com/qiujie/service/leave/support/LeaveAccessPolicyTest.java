package com.qiujie.service.leave.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请假越权与动作可及性判定纯逻辑单测（对齐 Mock {@code leaveStore} 的 guard 与派生标志）。
 * <p>
 * 覆盖：提交前置（ADMIN / 无归属拦截）、详情可见范围（ADMIN 全量 / 本人 / 本站站长）、
 * 编辑·撤销·撤回的派生标志、站长初审（跨站 / 审自己 / 状态）、老板终审（审自己 / 状态）。
 * <p>本机无 JDK/Maven，测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class LeaveAccessPolicyTest {

    private static final String ADMIN = "ADMIN";
    private static final String STATION_ADMIN = "STATION_ADMIN";
    private static final String STAFF = "STAFF";

    private static final String PENDING_STATION = LeaveConstants.STATUS_PENDING_STATION;
    private static final String PENDING_BOSS = LeaveConstants.STATUS_PENDING_BOSS;
    private static final String APPROVED = LeaveConstants.STATUS_APPROVED;
    private static final String REVOKED = LeaveConstants.STATUS_REVOKED;

    @Test
    @DisplayName("提交前置：ADMIN 不可提交；无归属驿站不可提交；STAFF/站长有归属可提交")
    void canSubmit() {
        assertFalse(LeaveAccessPolicy.canSubmit(ADMIN, 1L));
        assertFalse(LeaveAccessPolicy.canSubmit(STAFF, null));
        assertFalse(LeaveAccessPolicy.canSubmit(STATION_ADMIN, null));
        assertTrue(LeaveAccessPolicy.canSubmit(STAFF, 1L));
        assertTrue(LeaveAccessPolicy.canSubmit(STATION_ADMIN, 2L));
    }

    @Test
    @DisplayName("详情可见：ADMIN 全量 / 本人 / 本站站长；跨站员工不可见")
    void canView() {
        Long owner = 10L;
        Long station = 5L;
        assertTrue(LeaveAccessPolicy.canView(ADMIN, 999L, owner, station, null));
        assertTrue(LeaveAccessPolicy.canView(STAFF, owner, owner, station, station));
        assertTrue(LeaveAccessPolicy.canView(STATION_ADMIN, 20L, owner, station, station));
        assertFalse(LeaveAccessPolicy.canView(STATION_ADMIN, 20L, owner, station, 6L));
        assertFalse(LeaveAccessPolicy.canView(STAFF, 20L, owner, station, station));
        assertFalse(LeaveAccessPolicy.canView(STATION_ADMIN, 20L, owner, station, null));
    }

    @Test
    @DisplayName("编辑：本人 + 待初审；非本人或非待初审一律不可编辑")
    void canEdit() {
        assertTrue(LeaveAccessPolicy.canEdit(10L, 10L, PENDING_STATION));
        assertFalse(LeaveAccessPolicy.canEdit(10L, 10L, PENDING_BOSS));
        assertFalse(LeaveAccessPolicy.canEdit(11L, 10L, PENDING_STATION));
        assertFalse(LeaveAccessPolicy.canEdit(null, 10L, PENDING_STATION));
    }

    @Test
    @DisplayName("撤销：本人 + 两种待审态；已通过/终态不可撤")
    void canCancel() {
        assertTrue(LeaveAccessPolicy.canCancel(10L, 10L, PENDING_STATION));
        assertTrue(LeaveAccessPolicy.canCancel(10L, 10L, PENDING_BOSS));
        assertFalse(LeaveAccessPolicy.canCancel(10L, 10L, APPROVED));
        assertFalse(LeaveAccessPolicy.canCancel(11L, 10L, PENDING_STATION));
    }

    @Test
    @DisplayName("撤回：ADMIN + 已通过；站长或非已通过不可撤")
    void canRevoke() {
        assertTrue(LeaveAccessPolicy.canRevoke(ADMIN, APPROVED));
        assertFalse(LeaveAccessPolicy.canRevoke(STATION_ADMIN, APPROVED));
        assertFalse(LeaveAccessPolicy.canRevoke(ADMIN, PENDING_BOSS));
        assertFalse(LeaveAccessPolicy.canRevoke(ADMIN, REVOKED));
    }

    @Test
    @DisplayName("站长初审：本驿站 + 非本人 + 待初审；跨站/审自己/状态不符一律不可办")
    void canStationApprove() {
        assertTrue(LeaveAccessPolicy.canStationApprove(5L, 5L, 20L, 10L, PENDING_STATION));
        assertFalse(LeaveAccessPolicy.canStationApprove(6L, 5L, 20L, 10L, PENDING_STATION)); // 跨站
        assertFalse(LeaveAccessPolicy.canStationApprove(5L, 5L, 10L, 10L, PENDING_STATION)); // 审自己
        assertFalse(LeaveAccessPolicy.canStationApprove(5L, 5L, 20L, 10L, PENDING_BOSS));    // 状态不符
        assertFalse(LeaveAccessPolicy.canStationApprove(null, 5L, 20L, 10L, PENDING_STATION)); // 无归属
    }

    @Test
    @DisplayName("老板终审：非本人 + 待终审；审自己/状态不符不可办")
    void canFinalApprove() {
        assertTrue(LeaveAccessPolicy.canFinalApprove(20L, 10L, PENDING_BOSS));
        assertFalse(LeaveAccessPolicy.canFinalApprove(10L, 10L, PENDING_BOSS)); // 审自己
        assertFalse(LeaveAccessPolicy.canFinalApprove(20L, 10L, PENDING_STATION)); // 状态不符
    }

    @Test
    @DisplayName("同一人判定：null 安全")
    void owns() {
        assertTrue(LeaveAccessPolicy.owns(1L, 1L));
        assertFalse(LeaveAccessPolicy.owns(null, 1L));
        assertFalse(LeaveAccessPolicy.owns(1L, null));
        assertFalse(LeaveAccessPolicy.owns(null, null));
    }
}
