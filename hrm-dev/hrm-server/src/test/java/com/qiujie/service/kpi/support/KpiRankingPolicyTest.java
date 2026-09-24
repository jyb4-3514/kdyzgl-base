package com.qiujie.service.kpi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 竞赛排名单测（边界：同分同达成率共享名次、并列跳号、排序键）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class KpiRankingPolicyTest {

    private static KpiRankingPolicy.Entry entry(long id, String total, String rate) {
        return new KpiRankingPolicy.Entry(id, new BigDecimal(total), new BigDecimal(rate));
    }

    @Test
    @DisplayName("排序键：总分降序 → 达成率降序 → 员工 id 升序")
    void compare() {
        // 总分高者优先
        assertTrue(KpiRankingPolicy.compare(1, new BigDecimal("95.0"), new BigDecimal("0.90"),
                2, new BigDecimal("90.0"), new BigDecimal("0.95")) < 0);
        // 总分相同看达成率
        assertTrue(KpiRankingPolicy.compare(2, new BigDecimal("90.0"), new BigDecimal("0.95"),
                1, new BigDecimal("90.0"), new BigDecimal("0.90")) < 0);
        // 总分与达成率都相同看 id
        assertTrue(KpiRankingPolicy.compare(1, new BigDecimal("90.0"), new BigDecimal("0.90"),
                2, new BigDecimal("90.0"), new BigDecimal("0.90")) < 0);
    }

    @Test
    @DisplayName("竞赛排名：同「总分+达成率」共享名次，后续跳号（1-1-3 / 1-2-2）")
    void competitionRanks() {
        assertArrayEquals(new int[]{1, 1, 3}, KpiRankingPolicy.competitionRanks(List.of(
                entry(1, "90.0", "0.90"),
                entry(2, "90.0", "0.90"),
                entry(3, "80.0", "0.80"))));

        assertArrayEquals(new int[]{1, 2, 2}, KpiRankingPolicy.competitionRanks(List.of(
                entry(1, "90.0", "0.90"),
                entry(2, "85.0", "0.80"),
                entry(3, "85.0", "0.80"))));
    }

    @Test
    @DisplayName("同总分但达成率不同 → 不共享名次")
    void sameTotalDifferentRate() {
        assertArrayEquals(new int[]{1, 2}, KpiRankingPolicy.competitionRanks(List.of(
                entry(1, "90.0", "0.90"),
                entry(2, "90.0", "0.85"))));
    }
}
