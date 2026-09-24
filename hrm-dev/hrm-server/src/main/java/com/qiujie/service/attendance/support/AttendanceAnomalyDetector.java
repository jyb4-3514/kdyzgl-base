package com.qiujie.service.attendance.support;

import java.util.ArrayList;
import java.util.List;

/**
 * S4 · 考勤异常检测：稳健 z-score（median/MAD）+ 连续缺卡游程（与离线原型 {@code algo-scripts/s4-anomaly.mjs} 1:1 对应）。
 * <p>
 * 判据：
 * <pre>
 * 迟到频次:  robustZ(x) = (x − median(X)) / (1.4826 · MAD(X)) >= lateWarn（lateCritical 以上升级 CRITICAL）
 * 连续缺卡:  maxRun >= consecutiveAbsent
 * </pre>
 * 为什么用稳健统计而非 Isolation Forest/LOF：n≈200、特征 1~2 维，树/密度模型在此规模无精度优势且不可解释；
 * median/MAD 可直接手算复核。{@code TODO(扩展)}: 特征 ≥8 维（迟到/早退/缺卡/异常卡/时段分布）时再评估。
 * <p>
 * 失败降级（硬要求）：样本量 &lt; {@code minSamples} 或稳健尺度 = 0（全同值）→ <b>返回空集</b>，
 * 由上层回落单次静态阈值判定（{@code lateThresholdMin}），宁漏报不误报，避免给站长错误名单。
 * <p>
 * 复杂度：中位数/MAD 只算一次（一次排序 O(n log n)）+ O(n) 打分；切忌在循环内重复调 robustZ（会退化为 O(n² log n)）。
 */
public final class AttendanceAnomalyDetector {

    /** MAD → 标准差的正态一致性常数（Iglewicz & Hoaglin 1993） */
    private static final double MAD_NORMALIZER = 1.4826;

    private AttendanceAnomalyDetector() {
    }

    /** 检测超参（来源：{@code hrm.algo.attendance.anomaly.*}） */
    public record Config(boolean useRobust, double lateWarn, double lateCritical,
                         int consecutiveAbsent, int minSamples) {
    }

    /** 每员工在观察窗口内的聚合输入 */
    public record EmployeeData(Long employeeId, int lateCount, int maxAbsentRun) {
    }

    /**
     * 异常项。
     *
     * @param type     LATE_FREQUENT（迟到频次离群）/ ABSENT_RUN（连续缺卡）
     * @param severity WARN / CRITICAL
     */
    public record Anomaly(Long employeeId, String type, String severity, Double zValue,
                          Integer absentRun, String evidence) {
    }

    /** 检测报告（含分布指标，便于日志与单测核对） */
    public record Report(int sampleSize, double median, double mean, double std, double mad,
                         List<Anomaly> anomalies) {
    }

    /** 执行双检测器 */
    public static Report detect(List<EmployeeData> data, Config cfg) {
        if (data == null || data.isEmpty()) {
            return new Report(0, 0, 0, 0, 0, List.of());
        }
        double[] lateCounts = data.stream().mapToDouble(EmployeeData::lateCount).toArray();
        double median = quantile(lateCounts, 0.5);
        double mean = mean(lateCounts);
        double std = std(lateCounts);
        double mad = mad(lateCounts, median);

        // 降级 ①：样本不足 → 不出结论
        if (data.size() < cfg.minSamples()) {
            return new Report(data.size(), median, mean, std, mad, List.of());
        }

        List<Anomaly> anomalies = new ArrayList<>();

        // 检测器 1：迟到频次
        if (cfg.useRobust()) {
            double scale = MAD_NORMALIZER * mad;
            // 降级 ②：MAD=0（全同值）→ 无离群，回落静态规则
            if (scale > 0) {
                for (int i = 0; i < data.size(); i++) {
                    double z = (lateCounts[i] - median) / scale;
                    if (z >= cfg.lateWarn()) {
                        anomalies.add(lateAnomaly(data.get(i).employeeId(), z, cfg));
                    }
                }
            }
        } else {
            if (std > 0) {
                for (int i = 0; i < data.size(); i++) {
                    double z = (lateCounts[i] - mean) / std;
                    if (z >= cfg.lateWarn()) {
                        anomalies.add(lateAnomaly(data.get(i).employeeId(), z, cfg));
                    }
                }
            }
        }

        // 检测器 2：连续缺卡游程
        // TODO(扩展): 若需区分「严重连缺」（如 ≥2×阈值），待算法参数表增列 critical 阈值键后升级 severity。
        for (EmployeeData row : data) {
            if (row.maxAbsentRun() >= cfg.consecutiveAbsent()) {
                anomalies.add(new Anomaly(row.employeeId(), "ABSENT_RUN", "WARN", null, row.maxAbsentRun(),
                        "连续缺卡 " + row.maxAbsentRun() + " 天，达到预警阈值 " + cfg.consecutiveAbsent() + " 天"));
            }
        }
        return new Report(data.size(), median, mean, std, mad, anomalies);
    }

    /** 迟到异常项 + 严重度（z ≥ lateCritical 记 CRITICAL） */
    private static Anomaly lateAnomaly(Long employeeId, double z, Config cfg) {
        String severity = z >= cfg.lateCritical() ? "CRITICAL" : "WARN";
        return new Anomaly(employeeId, "LATE_FREQUENT", severity, round3(z), null,
                "迟到频次稳健 z=" + round3(z) + "，超过告警阈值 " + cfg.lateWarn());
    }

    // ==================== 统计口径（与 lib/stats.mjs 一致） ====================

    /** 线性插值分位数（type=7，与 Excel PERCENTILE.INC / numpy default 同法） */
    static double quantile(double[] values, double p) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        double idx = (sorted.length - 1) * Math.min(1, Math.max(0, p));
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted[lo];
        }
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    /** 中位绝对偏差 MAD（返回未乘 1.4826 的原始值） */
    static double mad(double[] values, double median) {
        if (values.length == 0) {
            return 0;
        }
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return quantile(deviations, 0.5);
    }

    static double mean(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** 样本标准差（n-1 分母）；n&lt;2 返回 0 */
    static double std(double[] values) {
        if (values.length < 2) {
            return 0;
        }
        double mean = mean(values);
        double acc = 0;
        for (double v : values) {
            acc += (v - mean) * (v - mean);
        }
        return Math.sqrt(acc / (values.length - 1));
    }

    private static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
