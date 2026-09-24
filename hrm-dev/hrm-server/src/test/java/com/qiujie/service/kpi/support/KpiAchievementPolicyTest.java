package com.qiujie.service.kpi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 达成率单测（边界：UP/DOWN、target=0、target 缺失、达成率 &gt;1 与 =1）。
 * <p>
 * 期望值与 Node 原型 {@code algo-scripts/s1-kpi.mjs} 的「target=0 边界」实测一致
 * （DOWN 0/0→1、DOWN 0/2→0、UP 0/0→0、UP 0/5→1）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class KpiAchievementPolicyTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("UP：达成率 = 实际/目标，保留 4 位小数，可 >1")
    void up() {
        assertEquals(d("0.0833"), KpiAchievementPolicy.achievement(d("100"), d("1200"), "UP"));
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("1200"), d("1200"), "UP"));
        assertEquals(d("2.0000"), KpiAchievementPolicy.achievement(d("2400"), d("1200"), "UP"));
    }

    @Test
    @DisplayName("UP：目标 ≤ 0 退化 —— 有数据即达标")
    void upTargetZero() {
        assertEquals(d("0.0000"), KpiAchievementPolicy.achievement(d("0"), d("0"), "UP"));
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("5"), d("0"), "UP"));
    }

    @Test
    @DisplayName("DOWN：实际 ≤ 目标记 1，否则 目标/实际")
    void down() {
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("0"), d("3"), "DOWN"));
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("3"), d("3"), "DOWN"));
        assertEquals(d("0.5000"), KpiAchievementPolicy.achievement(d("6"), d("3"), "DOWN"));
    }

    @Test
    @DisplayName("DOWN：target=0 —— 0 记 1，>0 记 0（与原型一致）")
    void downTargetZero() {
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("0"), d("0"), "DOWN"));
        assertEquals(d("0.0000"), KpiAchievementPolicy.achievement(d("2"), d("0"), "DOWN"));
    }

    @Test
    @DisplayName("target 缺失（null）：UP 有数据记 1；DOWN 无数据记 1")
    void targetNull() {
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("5"), null, "UP"));
        assertEquals(d("0.0000"), KpiAchievementPolicy.achievement(d("0"), null, "UP"));
        assertEquals(d("1.0000"), KpiAchievementPolicy.achievement(d("0"), null, "DOWN"));
        assertEquals(d("0.0000"), KpiAchievementPolicy.achievement(d("2"), null, "DOWN"));
    }
}
