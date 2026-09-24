package com.qiujie.service.kpi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分位映射单测（边界：全同值三种策略、n=1、混合并列的平均秩）。
 * <p>与原型 {@code s1-kpi.mjs} 实测一致：全同值 MID_RANK=0.5 / MIN=0 / MAX=1。
 * 注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class KpiQuantileMapperTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("全同值：MID_RANK=0.5000 / MIN=0.0000 / MAX=1.0000")
    void allEqual() {
        List<BigDecimal> values = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            values.add(d("0.75"));
        }
        assertEquals(d("0.5000"), KpiQuantileMapper.percentiles(values, "MID_RANK").get(0));
        assertEquals(d("0.0000"), KpiQuantileMapper.percentiles(values, "MIN").get(0));
        assertEquals(d("1.0000"), KpiQuantileMapper.percentiles(values, "MAX").get(0));
    }

    @Test
    @DisplayName("n=1：固定 0.5000")
    void single() {
        assertEquals(d("0.5000"), KpiQuantileMapper.percentiles(List.of(d("0.8")), "MID_RANK").get(0));
    }

    @Test
    @DisplayName("混合并列（平均秩）：[0.6,0.9,0.9,0.8] → [0,0.8333,0.8333,0.3333]")
    void mixed() {
        List<BigDecimal> out = KpiQuantileMapper.percentiles(
                List.of(d("0.6"), d("0.9"), d("0.9"), d("0.8")), "MID_RANK");
        assertEquals(d("0.0000"), out.get(0));
        assertEquals(d("0.8333"), out.get(1));
        assertEquals(d("0.8333"), out.get(2));
        assertEquals(d("0.3333"), out.get(3));
    }

    @Test
    @DisplayName("空集返回空；结果与入参同序")
    void emptyAndOrder() {
        assertEquals(0, KpiQuantileMapper.percentiles(List.of(), "MID_RANK").size());
    }
}
