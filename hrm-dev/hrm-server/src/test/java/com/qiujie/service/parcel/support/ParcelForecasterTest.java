package com.qiujie.service.parcel.support;

import com.qiujie.config.AlgoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包裹趋势预测器单测（算法 S7-1）。
 * <p>
 * 等价性对照：Holt-Winters 用例的期望值取自离线原型 {@code docs/algo-scripts/s7-parcel.mjs}
 * （同参数直接运行所得），与原型公式逐位一致（原型回测 MAPE 5.34%）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class ParcelForecasterTest {

    private static AlgoProperties.Forecast cfg(String model, int horizon) {
        AlgoProperties.Forecast cfg = new AlgoProperties.Forecast();
        cfg.setModel(model);
        cfg.setAlpha(0.5);
        cfg.setBeta(0.2);
        cfg.setGamma(0.4);
        cfg.setSeasonPeriod(7);
        cfg.setMinSamples(14);
        cfg.setMaWindow(7);
        cfg.setHorizonDays(horizon);
        return cfg;
    }

    @Test
    @DisplayName("Holt-Winters：与 Node 原型同参数结果一致（周期序列未来 3 步 = 10/20/30）")
    void holtWintersMatchesPrototype() {
        double[] series = {10, 20, 30, 40, 50, 60, 70, 10, 20, 30, 40, 50, 60, 70};
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(series, cfg("HOLT_WINTERS", 3));
        assertEquals(ParcelForecaster.MODEL_HOLT_WINTERS, result.model());
        assertArrayEquals(new double[]{10, 20, 30}, result.horizon(), 1e-6);
        assertEquals(series.length, result.fitted().length);
    }

    @Test
    @DisplayName("Holt-Winters：常数序列 → 预测与拟合恒为常数（不产生漂移/NaN）")
    void constantSeriesStaysConstant() {
        double[] series = new double[14];
        java.util.Arrays.fill(series, 5.0);
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(series, cfg("HOLT_WINTERS", 2));
        assertEquals(ParcelForecaster.MODEL_HOLT_WINTERS, result.model());
        assertArrayEquals(new double[]{5, 5}, result.horizon(), 1e-9);
        for (double v : result.fitted()) {
            assertEquals(5.0, v, 1e-9);
        }
    }

    @Test
    @DisplayName("降级①：历史点 < minSamples（2×季节周期）→ 回落 MA(窗口)")
    void degradesToMovingAverage() {
        double[] series = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}; // n=10 < 14
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(series, cfg("HOLT_WINTERS", 3));
        assertEquals(ParcelForecaster.MODEL_MA, result.model());
        // 最近 7 个点 (4..10) 均值 = 7.0
        assertArrayEquals(new double[]{7, 7, 7}, result.horizon(), 1e-9);
    }

    @Test
    @DisplayName("降级②：历史点 < MA 窗口 → 回落均值恒定预测")
    void degradesToMean() {
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(new double[]{1, 2, 3}, cfg("HOLT_WINTERS", 4));
        assertEquals(ParcelForecaster.MODEL_MEAN_FALLBACK, result.model());
        assertArrayEquals(new double[]{2, 2, 2, 2}, result.horizon(), 1e-9);
    }

    @Test
    @DisplayName("边界：空序列不报错（拟合空、预测全 0）")
    void emptySeriesIsSafe() {
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(new double[0], cfg("HOLT_WINTERS", 5));
        assertEquals(0, result.fitted().length);
        assertArrayEquals(new double[]{0, 0, 0, 0, 0}, result.horizon(), 1e-9);
    }

    @Test
    @DisplayName("边界：单点序列不报错（回落均值）")
    void singlePointIsSafe() {
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(new double[]{42}, cfg("HOLT_WINTERS", 2));
        assertEquals(ParcelForecaster.MODEL_MEAN_FALLBACK, result.model());
        assertArrayEquals(new double[]{42, 42}, result.horizon(), 1e-9);
    }

    @Test
    @DisplayName("边界：全零序列不产生 NaN")
    void allZeroSeriesNoNaN() {
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(new double[14], cfg("HOLT_WINTERS", 7));
        for (double v : result.horizon()) {
            assertFalse(Double.isNaN(v));
            assertEquals(0.0, v, 1e-9);
        }
    }

    @Test
    @DisplayName("健壮性：未知模型名回退 Holt-Winters，不抛错")
    void unknownModelFallsBack() {
        double[] series = new double[14];
        java.util.Arrays.fill(series, 8.0);
        ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(series, cfg("NOT_A_MODEL", 2));
        assertEquals(ParcelForecaster.MODEL_HOLT_WINTERS, result.model());
        assertTrue(result.horizon()[0] > 0);
    }
}
