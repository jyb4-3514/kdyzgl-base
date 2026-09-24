package com.qiujie.service.kpi;

import com.qiujie.entity.KpiMetric;

import java.math.BigDecimal;

/**
 * KPI 实际业绩值取数（算分的「数据源」入口，S1 §3.1 输入 {@code actual(e,m)}）。
 * <p>
 * 为什么要抽象：不同指标类型的数据源不同（考勤走 P3 真实明细、包裹/工单待业务域落地），
 * 把它们收敛到一个入口，算分内核只依赖本接口，不关心值从哪来。
 * 实现见 {@code service/kpi/impl/KpiActualValueResolver}。
 */
public interface KpiActualValueSource {

    /**
     * 取某员工某月某指标的实际业绩值。
     *
     * @param metric     指标配置（含类型/单位/方向/目标值）
     * @param employeeId 员工 id
     * @param month      考核月份 yyyy-MM
     */
    BigDecimal actualValue(KpiMetric metric, Long employeeId, String month);
}
