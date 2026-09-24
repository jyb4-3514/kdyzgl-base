package com.qiujie.dto.attendance;

import com.qiujie.dto.support.StationScopeQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡规则查询（GET /attendance/rule / GET /attendance/rule/list）。
 * <p>
 * 单条查询要求 {@code stationId}（缺省 → 400「缺少 stationId」）；非 ADMIN 由 L1 静默收敛为本人驿站。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceRuleQuery extends StationScopeQuery {
}
