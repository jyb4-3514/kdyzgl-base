package com.qiujie.service.kpi.support;

import com.qiujie.config.AlgoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单项得分 / 总分 / 等级单测（边界：三模式、线性封顶、单指标、加权归一、等级阈值）。
 * <p>
 * 期望值与 Node 原型 {@code s1-kpi.mjs} 实测一致：等价性回归 1200 项 0 不一致；
 * 极值 500% 达成率 LINEAR(cap=1)=100 / LINEAR(cap=1.2)=120 / TIERED=100；等级 90/80/70。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class KpiScorePolicyTest {

    private final AlgoProperties.Kpi cfg = new AlgoProperties().getKpi();

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("BINARY：达成率 ≥1 记满分，否则 0")
    void binary() {
        assertEquals(100, KpiScorePolicy.itemScore("BINARY", d("1"), d("100"), cfg));
        assertEquals(0, KpiScorePolicy.itemScore("BINARY", d("0.9999"), d("100"), cfg));
    }

    @Test
    @DisplayName("TIERED：阶梯系数 {1,0.9,0.8,0.6}（默认与外置参数等价）")
    void tiered() {
        assertEquals(100, KpiScorePolicy.itemScore("TIERED", d("1.0"), d("100"), cfg));
        assertEquals(90, KpiScorePolicy.itemScore("TIERED", d("0.95"), d("100"), cfg));
        assertEquals(80, KpiScorePolicy.itemScore("TIERED", d("0.85"), d("100"), cfg));
        assertEquals(60, KpiScorePolicy.itemScore("TIERED", d("0.7"), d("100"), cfg));
        assertEquals(0, KpiScorePolicy.itemScore("TIERED", d("0.5"), d("100"), cfg));
        // 满分非 100 时按比例：50 × 0.9 = 45
        assertEquals(45, KpiScorePolicy.itemScore("TIERED", d("0.9"), d("50"), cfg));
    }

    @Test
    @DisplayName("LINEAR：min(capRatio, 达成率) × 满分；默认 cap=1.0")
    void linear() {
        assertEquals(50, KpiScorePolicy.itemScore("LINEAR", d("0.5"), d("100"), cfg));
        assertEquals(100, KpiScorePolicy.itemScore("LINEAR", d("2.0"), d("100"), cfg));
        assertEquals(25, KpiScorePolicy.itemScore("LINEAR", d("0.5"), d("50"), cfg));
        // 达成率 500%，cap=1.2 → 120（与原型极值一致）
        AlgoProperties.Kpi cap12 = new AlgoProperties().getKpi();
        cap12.setLinearCapRatio(1.2);
        assertEquals(120, KpiScorePolicy.itemScore("LINEAR", d("5.0"), d("100"), cap12));
        assertEquals(100, KpiScorePolicy.itemScore("LINEAR", d("5.0"), d("100"), cfg));
    }

    @Test
    @DisplayName("总分：Σ(得分×权重)/Σ(权重)，保留 1 位小数（按适用权重归一）")
    void total() {
        assertEquals(d("84.0"), KpiScorePolicy.totalScore(List.of(
                new KpiScorePolicy.ScoreItem(100, 30),
                new KpiScorePolicy.ScoreItem(60, 20))));
        // 单指标：100 × 100 / 100 = 100.0（权重归一后仍 100 分制）
        assertEquals(d("100.0"), KpiScorePolicy.totalScore(List.of(new KpiScorePolicy.ScoreItem(100, 10))));
        // 空集：0
        assertEquals(d("0.0"), KpiScorePolicy.totalScore(List.of()));
    }

    @Test
    @DisplayName("加权分：得分×权重/100，保留 2 位小数")
    void weighted() {
        assertEquals(d("27.00"), KpiScorePolicy.weightedScore(90, 30));
        assertEquals(d("12.00"), KpiScorePolicy.weightedScore(60, 20));
    }

    @Test
    @DisplayName("等级：默认阈值 90/80/70 → EXCELLENT/GOOD/PASS/IMPROVE")
    void level() {
        assertEquals("EXCELLENT", KpiScorePolicy.level(d("90.0"), cfg.getLevels()));
        assertEquals("GOOD", KpiScorePolicy.level(d("89.9"), cfg.getLevels()));
        assertEquals("PASS", KpiScorePolicy.level(d("70.0"), cfg.getLevels()));
        assertEquals("IMPROVE", KpiScorePolicy.level(d("69.9"), cfg.getLevels()));
        assertEquals("IMPROVE", KpiScorePolicy.level(d("0.0"), cfg.getLevels()));
    }
}
