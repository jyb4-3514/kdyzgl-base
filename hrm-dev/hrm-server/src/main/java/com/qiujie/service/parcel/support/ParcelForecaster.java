package com.qiujie.service.parcel.support;

import com.qiujie.config.AlgoProperties;

import java.util.Arrays;

/**
 * 包裹趋势预测器（算法 S7-1，纯函数、无副作用、可离线单测）。
 * <p>
 * 选型与公式 1:1 对照离线原型 {@code docs/algo-scripts/s7-parcel.mjs}（Holt-Winters 加性季节，
 * 回测 MAPE 5.34%）：初始季节指数 = 各周期同位置均值 − 全局均值；水平/趋势初值取前 2 个周期的均值；
 * 每步先算「一步预测」作为拟合值，再更新 level/trend/season。
 * <p>
 * <b>降级链</b>（架构 §6.2 P10 验收⑤）：历史点 &lt; 2×季节周期 → 回落 MA(窗口) → 再不足 → 回落均值恒定预测。
 * 任何长度（含空序列/单点/全零）都返回结果<b>不抛错</b>；全零序列不产生 NaN。
 * <p>
 * 参数全部来自 {@code hrm.algo.parcel.forecast.*}（禁止内联阈值，规则 §11.4）。
 */
public final class ParcelForecaster {

    private ParcelForecaster() {
    }

    public static final String MODEL_HOLT_WINTERS = "HOLT_WINTERS";
    public static final String MODEL_HOLT = "HOLT";
    public static final String MODEL_MA = "MA";
    public static final String MODEL_SEASONAL_NAIVE = "SEASONAL_NAIVE";
    /** 降级到均值恒定预测时的模型标识 */
    public static final String MODEL_MEAN_FALLBACK = "MEAN";

    /**
     * 预测结果。
     *
     * @param model   实际生效的模型（含降级后的），便于观测与单测断言
     * @param fitted  与输入等长的拟合序列（一步预测），供趋势图叠加
     * @param horizon 未来 {@code horizonDays} 步预测
     */
    public record ForecastResult(String model, double[] fitted, double[] horizon) {
    }

    /** 按配置预测（入口） */
    public static ForecastResult forecast(double[] series, AlgoProperties.Forecast cfg) {
        int n = series == null ? 0 : series.length;
        int horizon = Math.max(0, cfg.getHorizonDays());
        if (n == 0) {
            // 空序列：零预测（空态不报错）
            return new ForecastResult(MODEL_MEAN_FALLBACK, new double[0], new double[horizon]);
        }
        int m = Math.max(1, cfg.getSeasonPeriod());
        String model = normalize(cfg.getModel());

        // 降级链：HW 需 ≥ max(2m, minSamples)；不足 → MA；MA 窗口仍不足 → 均值
        if (MODEL_HOLT_WINTERS.equals(model) && n < Math.max(2 * m, Math.max(2, cfg.getMinSamples()))) {
            model = MODEL_MA;
        }
        if (MODEL_MA.equals(model) && n < Math.max(1, cfg.getMaWindow())) {
            model = MODEL_MEAN_FALLBACK;
        }
        if (MODEL_HOLT.equals(model) && n < 2) {
            model = MODEL_MEAN_FALLBACK;
        }
        if (MODEL_SEASONAL_NAIVE.equals(model) && n < m) {
            model = MODEL_MA;
        }
        if (MODEL_MA.equals(model) && n < Math.max(1, cfg.getMaWindow())) {
            model = MODEL_MEAN_FALLBACK;
        }

        return switch (model) {
            case MODEL_HOLT_WINTERS -> holtWinters(series, cfg.getAlpha(), cfg.getBeta(), cfg.getGamma(), m, horizon);
            case MODEL_HOLT -> holt(series, cfg.getAlpha(), cfg.getBeta(), horizon);
            case MODEL_SEASONAL_NAIVE -> seasonalNaive(series, m, horizon);
            case MODEL_MA -> movingAverage(series, Math.max(1, cfg.getMaWindow()), horizon);
            default -> meanFallback(series, horizon);
        };
    }

    /** Holt-Winters 加性季节（O(n) 拟合 + O(horizon) 预测），来源 FPP3 §8.3 / statsmodels ExponentialSmoothing(additive) */
    static ForecastResult holtWinters(double[] s, double alpha, double beta, double gamma, int m, int horizon) {
        int n = s.length;
        int seasons = Math.max(1, n / m);
        double overall = ParcelMetrics.mean(s, 0, n);

        // 初始季节指数：各周期同位置均值 − 全局均值
        double[] season = new double[m];
        for (int i = 0; i < seasons; i++) {
            for (int j = 0; j < m; j++) {
                season[j] += s[i * m + j];
            }
        }
        for (int j = 0; j < m; j++) {
            season[j] = season[j] / seasons - overall;
        }

        double level = ParcelMetrics.mean(s, 0, m) - ParcelMetrics.mean(season);
        double trend = (ParcelMetrics.mean(s, m, 2 * m) - ParcelMetrics.mean(s, 0, m)) / m;

        double[] fitted = new double[n];
        for (int t = 0; t < n; t++) {
            int sIdx = t % m;
            double prevLevel = level;
            // 先记录一步预测（拟合值），再按本期实际更新，避免「用当期的自己预测当期」
            fitted[t] = level + trend + season[sIdx];
            level = alpha * (s[t] - season[sIdx]) + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
            season[sIdx] = gamma * (s[t] - level) + (1 - gamma) * season[sIdx];
        }

        double[] h = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            h[i] = level + (i + 1) * trend + season[(n + i) % m];
        }
        return new ForecastResult(MODEL_HOLT_WINTERS, fitted, h);
    }

    /** Holt 线性趋势（二次指数平滑，来源 FPP3 §8.2） */
    static ForecastResult holt(double[] s, double alpha, double beta, int horizon) {
        int n = s.length;
        double level = s[0];
        double trend = n >= 2 ? s[1] - s[0] : 0.0;
        double[] fitted = new double[n];
        fitted[0] = s[0];
        for (int t = 1; t < n; t++) {
            double prevLevel = level;
            fitted[t] = level + trend;
            level = alpha * s[t] + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
        }
        double[] h = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            h[i] = level + (i + 1) * trend;
        }
        return new ForecastResult(MODEL_HOLT, fitted, h);
    }

    /**
     * 移动平均：拟合取滚动窗均值，预测取「最近窗口均值」恒定值。
     * <p>
     * 与原型 {@code forecastMA} 的差异：原型按步平移窗口（h&gt;1 时窗口被截短），生产实现取标准恒定 MA；
     * 降级路径不参与 MAPE 基线（基线是 Holt-Winters），无等价性影响。
     */
    static ForecastResult movingAverage(double[] s, int w, int horizon) {
        int n = s.length;
        double[] fitted = new double[n];
        for (int t = 0; t < n; t++) {
            fitted[t] = ParcelMetrics.mean(s, Math.max(0, t - w + 1), t + 1);
        }
        double lastMa = ParcelMetrics.mean(s, Math.max(0, n - w), n);
        double[] h = new double[horizon];
        Arrays.fill(h, lastMa);
        return new ForecastResult(MODEL_MA, fitted, h);
    }

    /** 季节性朴素（对照基线）：预测为上一个周期同位置值 */
    static ForecastResult seasonalNaive(double[] s, int m, int horizon) {
        int n = s.length;
        double[] fitted = new double[n];
        for (int t = 0; t < n; t++) {
            fitted[t] = t >= m ? s[t - m] : s[t];
        }
        double[] h = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            h[i] = s[n - m + (i % m)];
        }
        return new ForecastResult(MODEL_SEASONAL_NAIVE, fitted, h);
    }

    /** 均值恒定预测（最终降级；单点/全零/超短序列的安全兜底） */
    static ForecastResult meanFallback(double[] s, int horizon) {
        double mu = ParcelMetrics.mean(s, 0, s.length);
        double[] fitted = new double[s.length];
        Arrays.fill(fitted, mu);
        double[] h = new double[horizon];
        Arrays.fill(h, mu);
        return new ForecastResult(MODEL_MEAN_FALLBACK, fitted, h);
    }

    /** 模型名归一：未知值回退默认 Holt-Winters（不因配置拼写错误而中断） */
    private static String normalize(String model) {
        if (model == null) {
            return MODEL_HOLT_WINTERS;
        }
        String upper = model.trim().toUpperCase();
        return switch (upper) {
            case MODEL_HOLT, MODEL_MA, MODEL_SEASONAL_NAIVE, MODEL_HOLT_WINTERS -> upper;
            default -> MODEL_HOLT_WINTERS;
        };
    }
}
