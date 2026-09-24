package com.qiujie.service.parcel.support;

/**
 * 包裹指标计算（纯函数，口径对齐 Mock {@code parcelStore.js}）。
 * <p>
 * 比率一律保留 4 位小数（Mock 用 {@code Number(x.toFixed(4))}），分母为 0 时返回 0（空态不报错）。
 */
public final class ParcelMetrics {

    private ParcelMetrics() {
    }

    /** 保留 4 位小数（四舍五入，对齐 Mock toFixed(4)） */
    public static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** 比率：分母 ≤ 0 返回 0.0（空表/无基数场景），否则四舍五入到 4 位 */
    public static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return round4((double) numerator / denominator);
    }

    /** 均值（空数组返回 0，不抛错） */
    public static double mean(double[] values) {
        return mean(values, 0, values == null ? 0 : values.length);
    }

    /** 区间均值：[from, to) 半开区间，越界自动收敛；空区间返回 0 */
    public static double mean(double[] values, int from, int to) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        int lo = Math.max(0, from);
        int hi = Math.min(values.length, to);
        if (hi <= lo) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = lo; i < hi; i++) {
            sum += values[i];
        }
        return sum / (hi - lo);
    }

    /** 平均绝对误差（回测口径，对齐 algo {@code stats.mjs#mae}） */
    public static double mae(double[] actual, double[] predicted) {
        if (actual == null || actual.length == 0 || predicted == null) {
            return 0.0;
        }
        double acc = 0.0;
        for (int i = 0; i < actual.length; i++) {
            acc += Math.abs(actual[i] - predicted[i]);
        }
        return acc / actual.length;
    }

    /**
     * 平均绝对百分比误差（对齐 algo {@code stats.mjs#mape}）：
     * 跳过实际值绝对值 &lt; 1e-9 的样本，避免除零放大（FPP3 §5.8）。
     */
    public static double mape(double[] actual, double[] predicted) {
        if (actual == null || actual.length == 0 || predicted == null) {
            return 0.0;
        }
        double acc = 0.0;
        int count = 0;
        for (int i = 0; i < actual.length; i++) {
            if (Math.abs(actual[i]) < 1e-9) {
                continue;
            }
            acc += Math.abs((actual[i] - predicted[i]) / actual[i]);
            count++;
        }
        return count == 0 ? 0.0 : acc / count;
    }
}
