package com.qiujie.service.workorder.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 SLA 计算与「超时未处理」判定单测（对齐 Mock {@code isOverdueUnhandled} 与
 * {@code WORK_ORDER_SLA_HOURS = {0:48, 1:24, 2:8}}）。
 * <p>
 * 边界覆盖：终态不计超时、SLA 恰好到点（不超时）、SLA 键缺失兜底、deadline 为空。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class WorkOrderSlaPolicyTest {

    /** 现状口径：低 48h / 中 24h / 高 8h（Q4 未裁定前的默认值） */
    private static final Map<Integer, Integer> DEFAULT_HOURS = new LinkedHashMap<>(Map.of(0, 48, 1, 24, 2, 8));
    private static final int FALLBACK = 24;

    @Test
    @DisplayName("SLA 截止：按优先级计算（48/24/8 小时）")
    void deadlineByPriority() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 24, 10, 0, 0);
        assertEquals(base.plusHours(48), WorkOrderSlaPolicy.deadline(base, 0, DEFAULT_HOURS, FALLBACK));
        assertEquals(base.plusHours(24), WorkOrderSlaPolicy.deadline(base, 1, DEFAULT_HOURS, FALLBACK));
        assertEquals(base.plusHours(8), WorkOrderSlaPolicy.deadline(base, 2, DEFAULT_HOURS, FALLBACK));
    }

    @Test
    @DisplayName("SLA 键缺失：回落到兜底小时数（不抛异常）")
    void missingKeyFallsBack() {
        Map<Integer, Integer> partial = new HashMap<>();
        partial.put(2, 8);
        assertEquals(8, WorkOrderSlaPolicy.resolveHours(2, partial, FALLBACK));
        assertEquals(FALLBACK, WorkOrderSlaPolicy.resolveHours(0, partial, FALLBACK));
        // 非正数同样视为缺失
        partial.put(0, 0);
        assertEquals(FALLBACK, WorkOrderSlaPolicy.resolveHours(0, partial, FALLBACK));
        // 配置整体为空
        assertEquals(FALLBACK, WorkOrderSlaPolicy.resolveHours(1, null, FALLBACK));
    }

    @Test
    @DisplayName("超时判定：仅未处理完（0/1）计入；终态（2/3）不计")
    void onlyOpenStatusesCount() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 24, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 9, 24, 12, 0, 0);
        assertTrue(WorkOrderSlaPolicy.isOverdueUnhandled(0, deadline, now));
        assertTrue(WorkOrderSlaPolicy.isOverdueUnhandled(1, deadline, now));
        assertFalse(WorkOrderSlaPolicy.isOverdueUnhandled(2, deadline, now));
        assertFalse(WorkOrderSlaPolicy.isOverdueUnhandled(3, deadline, now));
    }

    @Test
    @DisplayName("边界：SLA 恰好到点不算超时（严格大于）；未到点不算；deadline/status 为空不抛")
    void boundaryExactlyOnDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 24, 8, 0, 0);
        // now == deadline → 不超时（Mock：now > deadline 为严格大于）
        assertFalse(WorkOrderSlaPolicy.isOverdueUnhandled(1, deadline, deadline));
        assertFalse(WorkOrderSlaPolicy.isOverdueUnhandled(1, deadline, deadline.minusSeconds(1)));
        assertTrue(WorkOrderSlaPolicy.isOverdueUnhandled(1, deadline, deadline.plusSeconds(1)));
        // 空值安全
        assertFalse(WorkOrderSlaPolicy.isOverdueUnhandled(null, deadline, deadline.plusHours(1)));
        assertFalse(WorkOrderSlaPolicy.isOverdueUnhandled(1, null, deadline.plusHours(1)));
    }
}
