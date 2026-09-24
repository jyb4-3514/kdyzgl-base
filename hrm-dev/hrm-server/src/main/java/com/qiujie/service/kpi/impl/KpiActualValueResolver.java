package com.qiujie.service.kpi.impl;

import com.qiujie.entity.KpiMetric;
import com.qiujie.service.kpi.KpiActualValueSource;
import com.qiujie.service.kpi.support.KpiConstants;
import com.qiujie.service.kpi.support.KpiSimulatedData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * KPI 实际值取数分派器（唯一数据源入口）。
 * <p>
 * 分派规则：
 * <ul>
 *   <li>{@code ATTENDANCE} → 复用 P3 考勤能力（{@link KpiAttendanceMetricSource}），不重写考勤统计；</li>
 *   <li>其余类型 → 确定性模拟（{@link KpiSimulatedData}），待对应业务域落地后替换。</li>
 * </ul>
 * 把分派集中在此，算分内核不感知数据来源；未来接入真实业务表只改本类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiActualValueResolver implements KpiActualValueSource {

    private final KpiAttendanceMetricSource attendanceMetricSource;

    @Override
    public BigDecimal actualValue(KpiMetric metric, Long employeeId, String month) {
        if (KpiConstants.TYPE_ATTENDANCE.equals(metric.getMetricType())) {
            return attendanceMetricSource.qualificationRate(employeeId, month);
        }
        // TODO(扩展): 包裹(P7)/工单(P8)/服务评分/培训等业务域落地后，在此按类型分派到真实明细统计，
        //   并移除 KpiSimulatedData（模拟源仅为「尚无真实业务表」的过渡承载）。
        return KpiSimulatedData.actualValue(metric.getMetricKey(), employeeId, month,
                metric.getTargetValue(), metric.getUnit(), metric.getDirection());
    }
}
