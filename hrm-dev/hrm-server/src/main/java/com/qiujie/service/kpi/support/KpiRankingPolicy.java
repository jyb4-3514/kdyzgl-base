package com.qiujie.service.kpi.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * KPI 汇总排序与竞赛排名（纯逻辑，S1 §3.1/§3.2）。
 * <p>
 * 排序键与 Mock {@code summarize} 逐位一致：总分降序 → 达成率降序 → 员工 id 升序。
 * 排名用**竞赛排名法**（standard competition ranking，1-2-2-4）：同「总分 + 达成率」共享名次，
 * 后续名次跳号；避免同分却被分出先后。依据见算法 §3.3（Wikipedia: Ranking）。
 */
public final class KpiRankingPolicy {

    private KpiRankingPolicy() {
    }

    /** 排序比较：总分降序 → 达成率降序 → 员工 id 升序 */
    public static int compare(long id1, BigDecimal total1, BigDecimal rate1,
                              long id2, BigDecimal total2, BigDecimal rate2) {
        int byTotal = nz(total2).compareTo(nz(total1));
        if (byTotal != 0) {
            return byTotal;
        }
        int byRate = nz(rate2).compareTo(nz(rate1));
        if (byRate != 0) {
            return byRate;
        }
        return Long.compare(id1, id2);
    }

    /**
     * 竞赛排名：入参须已按 {@link #compare} 排好序；同「总分|达成率」共享名次，名次跳号。
     *
     * @return 与入参同序的名次数组（下标 0 = 第 1 名）
     */
    public static int[] competitionRanks(List<Entry> sortedEntries) {
        int size = sortedEntries.size();
        int[] ranks = new int[size];
        int rank = 0;
        String prevKey = null;
        for (int i = 0; i < size; i++) {
            Entry entry = sortedEntries.get(i);
            String key = nz(entry.totalScore()).toPlainString() + "|" + nz(entry.achievementRate()).toPlainString();
            if (!key.equals(prevKey)) {
                rank = i + 1;
            }
            ranks[i] = rank;
            prevKey = key;
        }
        return ranks;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 排名输入行（员工 id + 总分 + 达成率） */
    public record Entry(long employeeId, BigDecimal totalScore, BigDecimal achievementRate) {
    }
}
