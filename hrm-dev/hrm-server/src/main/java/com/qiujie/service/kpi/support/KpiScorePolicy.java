package com.qiujie.service.kpi.support;

import com.qiujie.config.AlgoProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * KPI 单项与总分评分（纯逻辑，S1 §3.1）。
 * <p>
 * 与原型的等价性：本类的 {@link #itemScore}/{@link #totalScore}/{@link #level} 与
 * {@code algo-scripts/s1-kpi.mjs#itemScore} 及基线 {@code baselineItemScore/baselineLevel} 在默认配置下
 * **逐项等价**（原型实测 1200 项 0 不一致）。差异仅在「阶梯」从硬编码改为 {@code hrm.algo.kpi.tieredTiers}
 * 查表、LINEAR 封顶从固定 1.0 改为 {@code linearCapRatio}（默认 1.0，行为不变）。
 * <p>
 * 分位（QUANTILE）为**全局开关**（{@code hrm.algo.kpi.quantile.enabled}），默认关闭；
 * 启用时由 {@link KpiQuantileMapper} 产出百分位后再走本类的 {@link #scoreFromPercentile}，
 * 不改变单指标 {@code scoreMode}（模式仍为 LINEAR/TIERED/BINARY）。
 */
public final class KpiScorePolicy {

    private KpiScorePolicy() {
    }

    /**
     * 单项得分（0..fullScore 的整数）。
     *
     * @param mode        LINEAR / TIERED / BINARY
     * @param achievement 达成率
     * @param fullScore   单项满分（null 视作 100，与 Mock 一致）
     * @param cfg         算法参数（阶梯 / 线性封顶）
     */
    public static int itemScore(String mode, BigDecimal achievement, BigDecimal fullScore, AlgoProperties.Kpi cfg) {
        BigDecimal full = fullScore == null ? BigDecimal.valueOf(100) : fullScore;
        BigDecimal rate = achievement == null ? BigDecimal.ZERO : achievement;
        if (KpiConstants.MODE_BINARY.equals(mode)) {
            return rate.compareTo(BigDecimal.ONE) >= 0 ? full.setScale(0, RoundingMode.HALF_UP).intValue() : 0;
        }
        if (KpiConstants.MODE_TIERED.equals(mode)) {
            for (AlgoProperties.Tier tier : cfg.getTieredTiers()) {
                if (rate.compareTo(BigDecimal.valueOf(tier.getMinAchievement())) >= 0) {
                    return round(full.multiply(BigDecimal.valueOf(tier.getRatio())));
                }
            }
            return 0;
        }
        // LINEAR：达成率按 linearCapRatio 封顶后线性折算（默认 1.0，与基线一致）
        double capped = Math.min(cfg.getLinearCapRatio(), rate.doubleValue());
        return round(full.multiply(BigDecimal.valueOf(capped)));
    }

    /** 分位模式单项得分：百分位 × 满分（百分位已由 {@link KpiQuantileMapper} 计出，∈[0,1]） */
    public static int scoreFromPercentile(BigDecimal percentile, BigDecimal fullScore) {
        BigDecimal full = fullScore == null ? BigDecimal.valueOf(100) : fullScore;
        BigDecimal p = percentile == null ? BigDecimal.ZERO : percentile;
        return round(full.multiply(p));
    }

    /**
     * 加权总分：{@code Σ(得分×权重) / Σ(权重)}（按员工**实际适用**指标权重归一，保留 1 位小数）。
     * <p>
     * 归一而非除以 100：适用角色不同的员工，总分仍可横向比较，不会因缺少某类指标被系统性压低。
     * 空集（权重合计 0）返回 0（与 Mock {@code group.weightSum ? ... : 0} 一致）。
     */
    public static BigDecimal totalScore(List<ScoreItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        long weightSum = 0;
        long weightedSum = 0;
        for (ScoreItem item : items) {
            weightSum += item.weight();
            weightedSum += (long) item.score() * item.weight();
        }
        if (weightSum == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(weightedSum)
                .divide(BigDecimal.valueOf(weightSum), 1, RoundingMode.HALF_UP);
    }

    /** 加权分：{@code 得分 × 权重 / 100}（保留 2 位小数，明细页展示用） */
    public static BigDecimal weightedScore(int score, int weight) {
        return BigDecimal.valueOf((long) score * weight)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** 等级映射：按 {@code levels}（降序）取首个 {@code total ≥ min} 的等级（无匹配回 null，正常配置必有 min=0 兜底） */
    public static String level(BigDecimal total, List<AlgoProperties.Level> levels) {
        BigDecimal safe = total == null ? BigDecimal.ZERO : total;
        for (AlgoProperties.Level level : levels) {
            if (safe.compareTo(BigDecimal.valueOf(level.getMin())) >= 0) {
                return level.getLevel();
            }
        }
        return null;
    }

    /** 四舍五入到整数（对齐 Mock {@code Math.round}，取值恒为非负） */
    private static int round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** 单指标得分项（score ∈ [0, fullScore]，weight 为该指标权重） */
    public record ScoreItem(int score, int weight) {
    }
}
