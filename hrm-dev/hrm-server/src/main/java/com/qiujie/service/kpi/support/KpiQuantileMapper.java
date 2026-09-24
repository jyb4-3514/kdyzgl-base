package com.qiujie.service.kpi.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 分位映射（纯逻辑，S1 §3.2「分位映射」）。
 * <p>
 * 把单个员工的达成率放到同 scope 的分布里取百分位（平均秩法处理并列）。与原型
 * {@code algo-scripts/s1-kpi.mjs#quantilePercentiles} 逐式等价：
 * <ul>
 *   <li>并列策略 MID_RANK（默认）：{@code p = less + (equal-1)/2}，全同值 → 0.5；</li>
 *   <li>MIN：{@code p = less}（全同值 → 0）；MAX：{@code p = less + equal - 1}（全同值 → 1）；</li>
 *   <li>n=1：固定 0.5；n=0：空集。</li>
 * </ul>
 * 复杂度 O(n log n)（排序）。**默认关闭**（{@code hrm.algo.kpi.quantile.enabled=false}，Q2 未裁定）；
 * 启用时样本量 &lt; {@code minSamples} 须回落绝对评分（由调用方判断）。
 * <p>
 * 注意：分位模式实测全员均分≈50，与 90/80/70 固定等级阈值严重不匹配（Q2），故未裁定前不得默认启用。
 */
public final class KpiQuantileMapper {

    /** 百分位小数位（与达成率同精度，便于对照） */
    private static final int SCALE = 4;

    private KpiQuantileMapper() {
    }

    /**
     * 计算每个元素在同分布中的百分位（与入参同序）。
     *
     * @param values 达成率序列（可含 null，按 0 处理）
     * @param policy MID_RANK / MIN / MAX
     */
    public static List<BigDecimal> percentiles(List<BigDecimal> values, String policy) {
        int size = values == null ? 0 : values.size();
        if (size == 0) {
            return List.of();
        }
        List<BigDecimal> safe = new ArrayList<>(size);
        for (BigDecimal value : values) {
            safe.add(value == null ? BigDecimal.ZERO : value);
        }

        // 按 (值升序, 原下标升序) 排序，保证并列分组与结果稳定
        List<Integer> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        order.sort((a, b) -> {
            int byValue = safe.get(a).compareTo(safe.get(b));
            return byValue != 0 ? byValue : Integer.compare(a, b);
        });

        BigDecimal[] bySortedPos = new BigDecimal[size];
        if (size == 1) {
            bySortedPos[0] = BigDecimal.valueOf(0.5).setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            int divisor = size - 1;
            int i = 0;
            while (i < size) {
                int j = i;
                while (j + 1 < size
                        && safe.get(order.get(j + 1)).compareTo(safe.get(order.get(i))) == 0) {
                    j++;
                }
                int less = i;
                int equal = j - i + 1;
                BigDecimal p;
                if ("MIN".equals(policy)) {
                    p = BigDecimal.valueOf(less).divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
                } else if ("MAX".equals(policy)) {
                    p = BigDecimal.valueOf((long) less + equal - 1)
                            .divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
                } else {
                    // MID_RANK：(less + (equal-1)/2) / (n-1) = (2*less + equal - 1) / (2*(n-1))
                    p = BigDecimal.valueOf(2L * less + equal - 1)
                            .divide(BigDecimal.valueOf(2L * divisor), SCALE, RoundingMode.HALF_UP);
                }
                for (int k = i; k <= j; k++) {
                    bySortedPos[k] = p;
                }
                i = j + 1;
            }
        }

        // 排序位 → 原序
        List<BigDecimal> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(null);
        }
        for (int pos = 0; pos < size; pos++) {
            out.set(order.get(pos), bySortedPos[pos]);
        }
        return out;
    }
}
