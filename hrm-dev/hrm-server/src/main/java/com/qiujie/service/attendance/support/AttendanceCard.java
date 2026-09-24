package com.qiujie.service.attendance.support;

import java.time.LocalDateTime;

/**
 * 打卡卡片的轻量视图（出勤口径/明细策略的纯输入）。
 * <p>
 * 为什么要这一层：出勤口径（应到/实到/迟到/早退/缺卡）与明细组装是纯聚合逻辑，
 * 直接依赖实体会让单测负担变重；本记录只保留判定所需的六列，便于用固定数据覆盖边界。
 * {@code status} 为 {@code NORMAL/LATE/EARLY_LEAVE/ABNORMAL}；异常卡（ABNORMAL）由调用方或策略过滤。
 */
public record AttendanceCard(
        Long employeeId,
        String checkType,
        String status,
        LocalDateTime checkTime,
        String periodName,
        String remark) {
}
