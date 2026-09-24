package com.qiujie.service.workorder.support;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工单 SLA 口径（纯逻辑，对齐 Mock {@code routes/workOrder.js} 的 {@code isOverdueUnhandled} 与
 * {@code db.js} 的 {@code sla_deadline = create_time + WORK_ORDER_SLA_HOURS[priority] * 3600000}）。
 * <p>
 * <b>口径 Q4 未裁定</b>：阈值一律从 {@code hrm.algo.dispatch.slaHours} 读，默认 {0:48, 1:24, 2:8} 与现状逐位等价
 * （algo-hrm-server.md §2 Q4 / §11.6）；本类<b>不含</b>硬编码小时数。
 * <p>
 * 「超 SLA（超时未处理）」判定 = <b>仅未处理完（状态 0/1）且 now &gt; deadline</b>；终态（2/3）不计入
 * ——终态继续计入会把「历史积压」误报成「待办超时」（Mock 注释原话）。
 * 边界：{@code now == deadline} 不算超时（Mock {@code now > deadline} 为严格大于）。
 */
public final class WorkOrderSlaPolicy {

    private WorkOrderSlaPolicy() {
    }

    /**
     * 解析某优先级的 SLA 小时数：优先取配置，缺失或非正数时回落到兜底值。
     * <p>
     * 为什么兜底：优先级键可能被运维在配置中删改，缺失时按非正数处理会让分母非法（除零 / 负时间窗）。
     */
    public static int resolveHours(int priority, Map<Integer, Integer> slaHours, int fallbackHours) {
        if (slaHours != null) {
            Integer hours = slaHours.get(priority);
            if (hours != null && hours > 0) {
                return hours;
            }
        }
        return fallbackHours > 0 ? fallbackHours : 24;
    }

    /** SLA 截止时间 = 基准时间 + SLA 小时数（基准为建单时间；企微自动派发为消息发送时间） */
    public static LocalDateTime deadline(LocalDateTime base, int priority,
                                         Map<Integer, Integer> slaHours, int fallbackHours) {
        return base.plusHours(resolveHours(priority, slaHours, fallbackHours));
    }

    /**
     * 是否「超时未处理」（Mock {@code isOverdueUnhandled} 的服务端等价）：
     * 未处理完（0/1）且 now 严格晚于 deadline。deadline 为空视为不超时（对齐 Mock {@code parseTime(null) → NaN} 比较恒假）。
     */
    public static boolean isOverdueUnhandled(Integer status, LocalDateTime deadline, LocalDateTime now) {
        if (status == null || deadline == null || now == null) {
            return false;
        }
        if (!WorkOrderStateMachine.isOpen(status)) {
            return false;
        }
        return now.isAfter(deadline);
    }
}
