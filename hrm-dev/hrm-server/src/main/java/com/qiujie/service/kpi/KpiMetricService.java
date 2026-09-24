package com.qiujie.service.kpi;

import com.qiujie.dto.kpi.KpiMetricBatchRequest;
import com.qiujie.dto.kpi.KpiMetricRequest;
import com.qiujie.vo.kpi.KpiMetricBatchResultVO;
import com.qiujie.vo.kpi.KpiMetricListVO;
import com.qiujie.vo.kpi.KpiMetricVO;

/**
 * KPI 指标配置服务（5 接口：列表 / 新增 / 编辑 / 删除 / 批量保存，仅 ADMIN）。
 * <p>
 * 权重守卫（启用合计须 = 目标）在「新增 / 编辑 / 删除 / 批量保存」四处校验，口径与 Mock {@code weightError} 一致；
 * 批量保存为**原子提交**（一次校验、全部成功或全部失败）。
 */
public interface KpiMetricService {

    /** 指标列表（含启用权重合计） */
    KpiMetricListVO list();

    /** 新增指标 */
    KpiMetricVO create(KpiMetricRequest request);

    /** 编辑指标（仅更新显式传入字段） */
    KpiMetricVO update(Long id, KpiMetricRequest request);

    /** 删除指标（逻辑删除；历史评分快照保留） */
    void remove(Long id);

    /** 批量保存（权重 + 启用状态），原子提交 */
    KpiMetricBatchResultVO saveBatch(KpiMetricBatchRequest request);
}
