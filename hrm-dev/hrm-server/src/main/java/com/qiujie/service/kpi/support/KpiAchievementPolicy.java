package com.qiujie.service.kpi.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 达成率计算（纯逻辑，S1 §3.1 目标函数首式）。
 * <p>
 * 与 Mock {@code kpiStore.achievementOf} 逐式等价（含 target 缺失 / 0 的退化口径）：
 * <ul>
 *   <li>UP：{@code actual / target}；target 缺失或 ≤ 0 时退化为「有数据即达标」（actual&gt;0 ⇒ 1，否则 0）；</li>
 *   <li>DOWN：{@code actual ≤ target ⇒ 1}，否则 {@code target / actual}；target 缺失时退化为「无数据才达标」
 *       （actual&gt;0 ⇒ 0，否则 1）。</li>
 * </ul>
 * 结果统一保留 4 位小数（对齐 Mock {@code toFixed(4)}），保证快照与竞赛排名键稳定可比。
 */
public final class KpiAchievementPolicy {

    /** 达成率小数位（与 Mock toFixed(4) 一致） */
    private static final int SCALE = 4;

    private KpiAchievementPolicy() {
    }

    /**
     * 计算达成率。
     *
     * @param actual    实际业绩值（非 null；取数来源见 {@code KpiActualValueResolver}）
     * @param target    目标值（可为 null，表示未配置）
     * @param direction UP / DOWN
     * @return 达成率（4 位小数）
     */
    public static BigDecimal achievement(BigDecimal actual, BigDecimal target, String direction) {
        BigDecimal safeActual = actual == null ? BigDecimal.ZERO : actual;
        if (KpiConstants.DIRECTION_DOWN.equals(direction)) {
            if (target == null) {
                // 目标缺失：缺乏可比基准，按「无数据（≤0）才算达标」退化（与 Mock 一致）
                return scale(safeActual.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ZERO : BigDecimal.ONE);
            }
            if (safeActual.compareTo(target) <= 0) {
                return scale(BigDecimal.ONE);
            }
            return target.divide(safeActual, SCALE, RoundingMode.HALF_UP);
        }
        // UP（默认）
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return scale(safeActual.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        return safeActual.divide(target, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
