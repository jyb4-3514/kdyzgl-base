package com.qiujie.service.leave.support;

/**
 * 半年单元整数区间（S5 建模核心，见 algo-hrm-server.md §7.1）。
 * <p>
 * 为什么落入一个不可变值对象：区间一旦算错（如同日 PM→AM 得到 endUnit &lt; startUnit）后续所有
 * 判定都会失真，用 {@link #valid()} 单一出口暴露「非法」，避免各处重复写 endUnit &gt;= startUnit。
 *
 * @param startUnit 首单元索引（含）；同日 AM→0、PM→1（unitIndex = epochDay × 2 + 0/1）
 * @param endUnit   末单元索引（含）
 */
public record LeaveUnitRange(long startUnit, long endUnit) {

    /** 区间合法：同日 PM→AM 会得到 endUnit &lt; startUnit，属非法组合（0 单元） */
    public boolean valid() {
        return endUnit >= startUnit;
    }

    /** 单元数（合法 2 的整数倍；非法为 0） */
    public long unitCount() {
        return valid() ? endUnit - startUnit + 1 : 0;
    }

    /** 自然天数（半天粒度 0.5） */
    public double naturalDays() {
        return unitCount() / 2.0;
    }
}
