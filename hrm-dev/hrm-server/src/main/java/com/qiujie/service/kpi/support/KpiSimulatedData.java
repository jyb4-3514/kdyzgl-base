package com.qiujie.service.kpi.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 确定性业绩数据源（模拟，Mock {@code kpiStore.actualValueOf} 的逐位等价实现）。
 * <p>
 * 背景：原始业绩值不入库——由「员工 + 月份 + 指标」哈希出的确定性序列模拟业务数据源，
 * 使「改了权重/目标值 → 重算 → 分数变化」的演示链路成立，且同参数重算结果恒定（刷新不变）。
 * <p>
 * 与 Mock 的实现逐位对齐（FNV-1a 种子 + mulberry32 PRNG，算法公开无依赖）：
 * <ul>
 *   <li>DOWN（越低越好）：{@code floor(rand × 4)} → 0-3 件；</li>
 *   <li>单位 %：{@code min(100, target × (0.86 + rand × 0.18))}，1 位小数；</li>
 *   <li>单位 分：{@code min(5, target × (0.88 + rand × 0.2))}，2 位小数；</li>
 *   <li>其它：{@code round(target × (0.55 + rand × 0.65))}。</li>
 * </ul>
 * <p>
 * <b>适用范围</b>：仅用于「尚无真实业务表」的指标类型（PARCEL/PICKUP/COMPLAINT/SERVICE/WORK_ORDER/
 * TRAINING/OTHER）。ATTENDANCE 类走 P3 考勤真实取数（见 {@code KpiAttendanceMetricSource}）。
 * TODO(扩展): 包裹（P7）/工单（P8）等业务域落地后，各类型改由真实明细实时统计，本类整体删除；
 *   调用入口统一收敛在 {@code KpiActualValueResolver}，届时只改该处。
 */
public final class KpiSimulatedData {

    private KpiSimulatedData() {
    }

    /**
     * 模拟实际业绩值。
     *
     * @param metricKey  指标键（参与哈希种子）
     * @param employeeId 员工 id（参与哈希种子）
     * @param month      考核月份 yyyy-MM（参与哈希种子）
     * @param targetValue 目标值（可为 null，按 0 处理）
     * @param unit        单位（% / 分 / 其它）
     * @param direction   方向（UP / DOWN）
     */
    public static BigDecimal actualValue(String metricKey, Long employeeId, String month,
                                         BigDecimal targetValue, String unit, String direction) {
        double seed = fnv1a(metricKey + "#" + employeeId + "#" + month);
        double rand = nextDouble((long) seed);
        double target = targetValue == null ? 0d : targetValue.doubleValue();

        if (KpiConstants.DIRECTION_DOWN.equals(direction)) {
            return BigDecimal.valueOf(Math.floor(rand * 4)).setScale(0, RoundingMode.HALF_UP);
        }
        if (KpiConstants.UNIT_PERCENT.equals(unit)) {
            return BigDecimal.valueOf(Math.min(100d, target * (0.86 + rand * 0.18)))
                    .setScale(1, RoundingMode.HALF_UP);
        }
        if (KpiConstants.UNIT_SCORE.equals(unit)) {
            return BigDecimal.valueOf(Math.min(5d, target * (0.88 + rand * 0.2)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(Math.round(target * (0.55 + rand * 0.65))).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * FNV-1a 字符串哈希（与 Mock {@code seedOf} / 原型 {@code fnv1a} 同实现），返回无符号 32 位。
     * Java int 乘法与 JS {@code Math.imul} 同为「取低 32 位」，故逐位等价。
     */
    public static long fnv1a(String text) {
        int hash = 0x811C9DC5; // 2166136261
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 16777619;
        }
        return Integer.toUnsignedLong(hash);
    }

    /**
     * mulberry32 单步随机：以无符号 32 位种子产出 [0,1)。
     * 与 Mock {@code createRandom}（mulberry32 变体）逐位等价。
     */
    private static double nextDouble(long seed) {
        int a = (int) (seed & 0xFFFFFFFFL) + 0x6D2B79F5;
        int t = a;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + ((t ^ (t >>> 7)) * (t | 61));
        return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0d;
    }
}
