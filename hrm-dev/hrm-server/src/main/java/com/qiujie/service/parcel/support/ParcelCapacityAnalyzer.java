package com.qiujie.service.parcel.support;

import java.util.Arrays;

/**
 * 驿站容量与热力分析（算法 S7-2，纯函数、可离线单测）。
 * <p>
 * 口径对齐算法方案 §9.1(b) 与离线原型 {@code s7-parcel.mjs#iqrOutliers}：
 * <ul>
 *   <li>利用率 {@code utilization = pendingPickup / shelfCapacity}；</li>
 *   <li>预警等级：{@code CRITICAL ≥ utilCritical}、{@code WARN ≥ utilWarn}、否则 {@code OK}；</li>
 *   <li>离群：Tukey 箱线图准则，落在 {@code [Q1 − k·IQR, Q3 + k·IQR]} 之外（k 来自配置，默认 1.5）。</li>
 * </ul>
 * 边界：全站同一利用率（IQR=0 → 无离群）、单驿站、容量为 0（利用率 0）、空集合（返回全零），均不抛错。
 */
public final class ParcelCapacityAnalyzer {

    private ParcelCapacityAnalyzer() {
    }

    /**
     * IQR 结果。
     *
     * @param q1      下四分位
     * @param q3      上四分位
     * @param iqr     四分位距（q3−q1）
     * @param low     下界（q1 − k·iqr）
     * @param high    上界（q3 + k·iqr）
     * @param outlier 与输入等长的离群标记（true=离群）
     */
    public record IqrResult(double q1, double q3, double iqr, double low, double high, boolean[] outlier) {
    }

    /** 利用率：容量 ≤ 0（未配置）视为 0，避免除零 */
    public static double utilization(long pendingPickup, int shelfCapacity) {
        if (shelfCapacity <= 0) {
            return 0.0;
        }
        return (double) Math.max(0, pendingPickup) / shelfCapacity;
    }

    /** 预警等级（阈值来自 {@code hrm.algo.parcel.capacity.*}） */
    public static String level(double utilization, double utilWarn, double utilCritical) {
        if (utilization >= utilCritical) {
            return ParcelConstants.LEVEL_CRITICAL;
        }
        if (utilization >= utilWarn) {
            return ParcelConstants.LEVEL_WARN;
        }
        return ParcelConstants.LEVEL_OK;
    }

    /** IQR 离群（空集合返回全零结果，不抛错） */
    public static IqrResult iqr(double[] values, double k) {
        int n = values == null ? 0 : values.length;
        if (n == 0) {
            return new IqrResult(0.0, 0.0, 0.0, 0.0, 0.0, new boolean[0]);
        }
        double q1 = quantile(values, 0.25);
        double q3 = quantile(values, 0.75);
        double iqr = q3 - q1;
        double low = q1 - k * iqr;
        double high = q3 + k * iqr;
        boolean[] outlier = new boolean[n];
        for (int i = 0; i < n; i++) {
            outlier[i] = values[i] < low || values[i] > high;
        }
        return new IqrResult(q1, q3, iqr, low, high, outlier);
    }

    /**
     * 线性插值分位数（type=7，对齐算法原型 {@code stats.mjs#quantile}，与 Excel PERCENTILE.INC / numpy 默认一致）。
     * 空数组返回 0；p 收敛到 [0,1]。
     */
    public static double quantile(double[] values, double p) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double idx = (sorted.length - 1) * Math.min(1.0, Math.max(0.0, p));
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted[lo];
        }
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }
}
