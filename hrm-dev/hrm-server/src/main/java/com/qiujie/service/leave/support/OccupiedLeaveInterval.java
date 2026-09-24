package com.qiujie.service.leave.support;

/**
 * 参与重叠判定的「占用区间」引用（候选请假单 + 其半天单元区间）。
 * <p>
 * 仅承载重叠筛查所需字段，不暴露完整实体，便于纯逻辑单测与区间相交筛查（{@code intersectsAny} / {@code firstIntersecting}）。
 *
 * @param leaveId 请假单 id（命中后用于回填重叠提示）
 * @param range   该单的半天单元区间
 */
public record OccupiedLeaveInterval(long leaveId, LeaveUnitRange range) {
}
