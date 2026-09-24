package com.qiujie.service.workorder.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单越权判定单测（对齐 Mock {@code canView/canAssign/canManage} + 转单同站约束）。
 * <p>
 * 覆盖：ADMIN 全量、站长限本站、员工仅限本人为处理人、指派仅 ADMIN/站长、转单跨站仅 ADMIN。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class WorkOrderAccessPolicyTest {

    private static final Long STATION_1 = 1L;
    private static final Long STATION_2 = 2L;

    @Test
    @DisplayName("详情可见：ADMIN 全量；站长/员工须同站（跨站 false → Service 回 404）")
    void canView() {
        assertTrue(WorkOrderAccessPolicy.canView("ADMIN", STATION_1, STATION_2));
        assertTrue(WorkOrderAccessPolicy.canView("STATION_ADMIN", STATION_1, STATION_1));
        assertFalse(WorkOrderAccessPolicy.canView("STATION_ADMIN", STATION_1, STATION_2));
        assertFalse(WorkOrderAccessPolicy.canView("STAFF", STATION_1, STATION_2));
        assertTrue(WorkOrderAccessPolicy.canView("STAFF", STATION_1, STATION_1));
        assertFalse(WorkOrderAccessPolicy.canView("STATION_ADMIN", null, STATION_1));
    }

    @Test
    @DisplayName("指派权限：仅 ADMIN / 本站站长；员工与跨站站长 false（→ 8002）")
    void canAssign() {
        assertTrue(WorkOrderAccessPolicy.canAssign("ADMIN", STATION_1, STATION_2));
        assertTrue(WorkOrderAccessPolicy.canAssign("STATION_ADMIN", STATION_1, STATION_1));
        assertFalse(WorkOrderAccessPolicy.canAssign("STATION_ADMIN", STATION_1, STATION_2));
        assertFalse(WorkOrderAccessPolicy.canAssign("STAFF", STATION_1, STATION_1));
    }

    @Test
    @DisplayName("流转/转单权限：ADMIN / 本站站长 / 当前处理人本人（→ 8002 / 8003）")
    void canManage() {
        Long assignee = 100L;
        assertTrue(WorkOrderAccessPolicy.canManage("ADMIN", STATION_1, 1L, STATION_2, assignee));
        assertTrue(WorkOrderAccessPolicy.canManage("STATION_ADMIN", STATION_1, 2L, STATION_1, assignee));
        assertFalse(WorkOrderAccessPolicy.canManage("STATION_ADMIN", STATION_1, 2L, STATION_2, assignee));
        // 普通员工：本人为处理人
        assertTrue(WorkOrderAccessPolicy.canManage("STAFF", STATION_1, assignee, STATION_1, assignee));
        assertFalse(WorkOrderAccessPolicy.canManage("STAFF", STATION_1, 999L, STATION_1, assignee));
        // 无处理人时员工不能操作
        assertFalse(WorkOrderAccessPolicy.canManage("STAFF", STATION_1, 999L, STATION_1, null));
    }

    @Test
    @DisplayName("转单同站约束：ADMIN 可跨站；站长/员工仅同站（跨站 → 8004）")
    void canTransferTo() {
        assertTrue(WorkOrderAccessPolicy.canTransferTo("ADMIN", STATION_2, STATION_1));
        assertTrue(WorkOrderAccessPolicy.canTransferTo("STATION_ADMIN", STATION_1, STATION_1));
        assertFalse(WorkOrderAccessPolicy.canTransferTo("STATION_ADMIN", STATION_2, STATION_1));
        assertFalse(WorkOrderAccessPolicy.canTransferTo("STAFF", STATION_2, STATION_1));
    }
}
