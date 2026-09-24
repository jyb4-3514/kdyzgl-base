package com.qiujie.service.parcel.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 包裹指标计算单测（比率 4 位小数 / 空态归零 / MAE / MAPE）。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class ParcelMetricsTest {

    @Test
    @DisplayName("rate：分母 ≤0 归零；否则保留 4 位小数")
    void rate() {
        assertEquals(0.0, ParcelMetrics.rate(5, 0), 1e-9);
        assertEquals(0.3, ParcelMetrics.rate(3, 10), 1e-9);
        assertEquals(0.3333, ParcelMetrics.rate(1, 3), 1e-9);
    }

    @Test
    @DisplayName("round4：四舍五入到 4 位")
    void round4() {
        assertEquals(0.3333, ParcelMetrics.round4(1.0 / 3), 1e-9);
        assertEquals(0.0322, ParcelMetrics.round4(0.03215), 1e-9);
    }

    @Test
    @DisplayName("mean：空数组归零；区间半开收敛")
    void mean() {
        assertEquals(0.0, ParcelMetrics.mean(new double[0]), 1e-9);
        assertEquals(2.0, ParcelMetrics.mean(new double[]{1, 2, 3}), 1e-9);
        assertEquals(2.5, ParcelMetrics.mean(new double[]{1, 2, 3, 4}, 1, 3), 1e-9);
    }

    @Test
    @DisplayName("MAE / MAPE：MAPE 跳过实际值接近 0 的样本（避免除零放大）")
    void errorMetrics() {
        double[] actual = {10, 20};
        double[] pred = {12, 18};
        assertEquals(2.0, ParcelMetrics.mae(actual, pred), 1e-9);
        assertEquals(0.15, ParcelMetrics.mape(actual, pred), 1e-9);

        double[] withZero = {0, 10};
        double[] pred2 = {5, 20};
        // 首个样本实际值=0 被跳过，仅计 |10-20|/10 = 1.0
        assertEquals(1.0, ParcelMetrics.mape(withZero, pred2), 1e-9);
    }
}
