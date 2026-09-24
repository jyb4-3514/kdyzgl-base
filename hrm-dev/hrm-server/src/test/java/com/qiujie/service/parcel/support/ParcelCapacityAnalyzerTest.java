package com.qiujie.service.parcel.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驿站容量与热力分析单测（算法 S7-2，IQR / 利用率 / 预警分级）。
 * <p>
 * IQR 期望值与 Node 原型 {@code s7-parcel.mjs#iqrOutliers} 同参数一致。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class ParcelCapacityAnalyzerTest {

    // ---------- 利用率 ----------

    @Test
    @DisplayName("利用率：pending / shelfCapacity；容量为 0 或负 pending 安全归零")
    void utilization() {
        assertEquals(0.5, ParcelCapacityAnalyzer.utilization(450, 900), 1e-9);
        assertEquals(0.0, ParcelCapacityAnalyzer.utilization(100, 0), 1e-9);
        assertEquals(0.0, ParcelCapacityAnalyzer.utilization(-5, 900), 1e-9);
    }

    @Test
    @DisplayName("预警分级：OK / WARN / CRITICAL 边界（0.8 与 0.95 取等号入级）")
    void level() {
        assertEquals(ParcelConstants.LEVEL_OK, ParcelCapacityAnalyzer.level(0.79, 0.8, 0.95));
        assertEquals(ParcelConstants.LEVEL_WARN, ParcelCapacityAnalyzer.level(0.8, 0.8, 0.95));
        assertEquals(ParcelConstants.LEVEL_WARN, ParcelCapacityAnalyzer.level(0.94, 0.8, 0.95));
        assertEquals(ParcelConstants.LEVEL_CRITICAL, ParcelCapacityAnalyzer.level(0.95, 0.8, 0.95));
        assertEquals(ParcelConstants.LEVEL_CRITICAL, ParcelCapacityAnalyzer.level(1.2, 0.8, 0.95));
    }

    // ---------- IQR ----------

    @Test
    @DisplayName("IQR：与原型一致（{1..6,100}, k=1.5 → q1=2.5/q3=5.5/low=-2/high=10，仅 100 离群）")
    void iqrMatchesPrototype() {
        double[] values = {1, 2, 3, 4, 5, 6, 100};
        ParcelCapacityAnalyzer.IqrResult r = ParcelCapacityAnalyzer.iqr(values, 1.5);
        assertEquals(2.5, r.q1(), 1e-9);
        assertEquals(5.5, r.q3(), 1e-9);
        assertEquals(3.0, r.iqr(), 1e-9);
        assertEquals(-2.0, r.low(), 1e-9);
        assertEquals(10.0, r.high(), 1e-9);
        assertFalse(r.outlier()[0]);
        assertTrue(r.outlier()[6]);
        assertEquals(7, r.outlier().length);
    }

    @Test
    @DisplayName("IQR 边界：全站同值（IQR=0）无离群；单驿站无离群；空集合返回全零")
    void iqrEdgeCases() {
        ParcelCapacityAnalyzer.IqrResult same = ParcelCapacityAnalyzer.iqr(new double[]{0.6, 0.6, 0.6}, 1.5);
        assertEquals(0.0, same.iqr(), 1e-9);
        for (boolean o : same.outlier()) {
            assertFalse(o);
        }

        ParcelCapacityAnalyzer.IqrResult single = ParcelCapacityAnalyzer.iqr(new double[]{0.9}, 1.5);
        assertFalse(single.outlier()[0]);

        ParcelCapacityAnalyzer.IqrResult empty = ParcelCapacityAnalyzer.iqr(new double[0], 1.5);
        assertEquals(0.0, empty.q1(), 1e-9);
        assertEquals(0, empty.outlier().length);
    }

    @Test
    @DisplayName("分位数：type=7 线性插值（{1,2,3,4} p=0.25 → 1.75）")
    void quantileType7() {
        assertEquals(1.75, ParcelCapacityAnalyzer.quantile(new double[]{1, 2, 3, 4}, 0.25), 1e-9);
        assertEquals(2.5, ParcelCapacityAnalyzer.quantile(new double[]{1, 2, 3, 4}, 0.5), 1e-9);
        assertEquals(0.0, ParcelCapacityAnalyzer.quantile(new double[0], 0.5), 1e-9);
    }
}
