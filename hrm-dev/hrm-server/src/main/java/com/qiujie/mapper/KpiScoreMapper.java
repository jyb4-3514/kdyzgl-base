package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.KpiScore;

/**
 * KPI 月度评分 Mapper（db.md §8.4.2）。
 * 明细/(employee,month)、列表与范围收敛/(month,station)、排行/(month,total_score) 的过滤与排序由 Service 构造。
 */
public interface KpiScoreMapper extends BaseMapper<KpiScore> {
}
