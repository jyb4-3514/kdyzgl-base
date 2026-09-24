package com.qiujie.service.kpi.support;

import java.util.List;

/**
 * 权重守卫（纯逻辑，S1 §3.1 约束 1）。
 * <p>
 * 口径与 Mock {@code weightError} 一致：**无启用指标时不校验**（放行，算分再回 9203 NO_METRIC）；
 * 有启用指标时合计须 = {@code weightSumTarget}（容差 {@code weightSumTolerance}）。
 * <p>
 * 为什么只在「新增/更新/删除/批量保存」校验、不在每次改权重时强校验：页面调权重天然要多步，
 * 逐条强校验会让权重无法调整；算分按适用权重归一，临时偏离不会算错分（页面按 weightSum 提示即可）。
 */
public final class KpiWeightGuard {

    private KpiWeightGuard() {
    }

    /**
     * 校验启用指标权重合计。
     *
     * @param enabledWeights 启用指标的权重集（无启用指标传空/null）
     * @param target         合计目标（默认 100）
     * @param tolerance      容差（默认 0）
     */
    public static Result check(List<Integer> enabledWeights, int target, int tolerance) {
        if (enabledWeights == null || enabledWeights.isEmpty()) {
            // 无启用指标：不校验（与 Mock 一致，交由算分报 NO_METRIC）
            return new Result(true, 0, null);
        }
        int sum = 0;
        for (Integer weight : enabledWeights) {
            sum += weight == null ? 0 : weight;
        }
        if (Math.abs(sum - target) <= tolerance) {
            return new Result(true, sum, null);
        }
        return new Result(false, sum, "启用指标合计须为 " + target + "%，当前为 " + sum + "%");
    }

    /** 守卫结果：pass=false 时 message 为可直接返给前端的文案（含当前合计） */
    public record Result(boolean pass, int enabledSum, String message) {
    }
}
