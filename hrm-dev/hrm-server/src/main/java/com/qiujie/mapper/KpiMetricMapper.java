package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.KpiMetric;

/**
 * KPI 指标配置 Mapper（db.md §8.4.1）。
 * 列表按 (enabled, sort_order)、键查重按 metric_key 的过滤条件由 Service 构造（不建物化外键）。
 */
public interface KpiMetricMapper extends BaseMapper<KpiMetric> {
}
