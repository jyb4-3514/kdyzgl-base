package com.qiujie.service.leave.support;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 请假半天单元「区间算法」（S5，algo-hrm-server.md §7；原型 algo-scripts/s5-leave.mjs）。
 * <p>
 * <b>核心变换</b>：把「日期 + 上下午」映射为整数单元 {@code unitIndex(date, period) = epochDay × 2 + (AM?0:1)}，
 * 一个请假区间即一段连续整数区间，于是：
 * <ul>
 *   <li>自然天数 = 区间长度 / 2，<b>O(1)</b>；</li>
 *   <li>计薪天数 = 在「已排序排班单元数组」上两次二分（lower_bound / upper_bound），<b>O(log m)</b>；</li>
 *   <li>重叠判定 = 区间相交 {@code a.start ≤ b.end ∧ b.start ≤ a.end}，<b>O(1)</b>；有序集合上二分为 <b>O(log k)</b>；</li>
 *   <li>账期跨度 = 单元区间连续 ⇒ 月份连续，<b>O(1)</b>（跨月单 ≤ 2 个月）。</li>
 * </ul>
 * <b>等价性</b>：本类的区间法与 Mock {@code halfUnitsOf} 的逐日生成语义逐例等价（参见单测
 * {@code LeaveIntervalPolicyTest} 与 S5 原型 20000 次随机查询 0 差异）。属等价加速，不是口径变更。
 * <p>
 * 为什么用 {@link LocalDate#toEpochDay()} 而非固定基准日：与架构 R-8 一致（本地时区语义，不做字符串日期解析），
 * 且无需维护「基准日」这一隐藏状态。
 */
public final class LeaveIntervalPolicy {

    private LeaveIntervalPolicy() {
    }

    // ==================== 日期 ↔ 单元索引 ====================

    /** 日序（= epochDay，本地时区语义） */
    public static long dayOrdinal(LocalDate date) {
        return date.toEpochDay();
    }

    /** 单元索引：AM = 日序 × 2，PM = 日序 × 2 + 1 */
    public static long unitOf(LocalDate date, String period) {
        return dayOrdinal(date) * 2L + (LeaveConstants.PERIOD_AM.equals(period) ? 0L : 1L);
    }

    /** 单元索引 → 所属日期 */
    public static LocalDate dateOfUnit(long unit) {
        return LocalDate.ofEpochDay(Math.floorDiv(unit, 2L));
    }

    /**
     * 请假区间 → 半天单元区间。
     * 起始日 PM 则首日无 AM（first=startDate#PM）；结束日 AM 则末日无 PM（last=endDate#AM）；
     * 同日 PM→AM 得 first &gt; last，{@link LeaveUnitRange#valid()} 为 false（非法，0 单元）。
     */
    public static LeaveUnitRange unitRange(LocalDate startDate, String startPeriod, LocalDate endDate, String endPeriod) {
        return new LeaveUnitRange(unitOf(startDate, startPeriod), unitOf(endDate, endPeriod));
    }

    // ==================== 自然天数 / 计薪天数 ====================

    /** 自然天数（O(1)） */
    public static double naturalDays(LocalDate startDate, String startPeriod, LocalDate endDate, String endPeriod) {
        return unitRange(startDate, startPeriod, endDate, endPeriod).naturalDays();
    }

    /**
     * 基线：逐日生成半天单元序列（与 Mock {@code halfUnitsOf} 同实现，供等价性回归对照）。
     * 仅测试与对照使用，生产路径走区间法。
     */
    public static List<Long> halfUnitsByDay(LocalDate startDate, String startPeriod, LocalDate endDate, String endPeriod) {
        List<Long> units = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            if (!(day.equals(startDate) && LeaveConstants.PERIOD_PM.equals(startPeriod))) {
                units.add(unitOf(day, LeaveConstants.PERIOD_AM));
            }
            if (!(day.equals(endDate) && LeaveConstants.PERIOD_AM.equals(endPeriod))) {
                units.add(unitOf(day, LeaveConstants.PERIOD_PM));
            }
        }
        return units;
    }

    /**
     * 排班日集合 → 升序单元数组（日粒度：每个排班日贡献 AM/PM 两个单元）。
     * 预排序一次性 O(m log m)，查询阶段与区间长度无关。
     */
    public static long[] scheduleUnits(Collection<LocalDate> scheduledDays) {
        long[] days = scheduledDays.stream().distinct().mapToLong(LocalDate::toEpochDay).sorted().toArray();
        long[] units = new long[days.length * 2];
        for (int i = 0; i < days.length; i++) {
            units[i * 2] = days[i] * 2;
            units[i * 2 + 1] = days[i] * 2 + 1;
        }
        return units;
    }

    /**
     * 算法化计薪天数：在升序排班单元数组上做两次二分（O(log m)）。
     *
     * @param schedUnitsSorted 升序排班单元数组（{@link #scheduleUnits}）
     * @param range            请假单元区间（非法返回 0）
     */
    public static double countedDaysByRange(long[] schedUnitsSorted, LeaveUnitRange range) {
        if (!range.valid() || schedUnitsSorted == null || schedUnitsSorted.length == 0) {
            return 0;
        }
        int lo = lowerBound(schedUnitsSorted, range.startUnit());
        int hi = upperBound(schedUnitsSorted, range.endUnit());
        return (hi - lo) / 2.0;
    }

    /** 基线计薪天数：逐日生成单元并按「日期 ∈ 排班日集合」过滤（O(L)），仅供等价性对照 */
    public static double countedDaysBaseline(Collection<LocalDate> scheduledDays,
                                             LocalDate startDate, String startPeriod,
                                             LocalDate endDate, String endPeriod) {
        long hit = 0;
        for (long unit : halfUnitsByDay(startDate, startPeriod, endDate, endPeriod)) {
            if (scheduledDays.contains(dateOfUnit(unit))) {
                hit++;
            }
        }
        return hit / 2.0;
    }

    // ==================== 重叠判定 ====================

    /** 两区间是否相交（含端点；首尾相接算重叠，如 [..PM] 与 [次日 AM..] 不相交，但同日 PM 与 AM 会相交） */
    public static boolean intersects(LeaveUnitRange a, LeaveUnitRange b) {
        if (!a.valid() || !b.valid()) {
            return false;
        }
        return a.startUnit() <= b.endUnit() && b.startUnit() <= a.endUnit();
    }

    /**
     * 区间相交存在性（O(log k)）：占用区间按 {@code startUnit} 升序 + 终点前缀最大值，二分后一次比较。
     * <p>
     * 正确性：任何与 target 相交的区间必有 {@code start_i ≤ target.end}（否则在右侧完全不相交）；
     * 在所有满足该条件的前缀区间中，只要其 end 的最大值 ≥ {@code target.start}，即存在相交——
     * 这是区间树在「只需布尔相交」场景下的退化形式（CLRS ch.14；见 algo-hrm-server.md §7.3）。
     *
     * @param sortedByStart 按 {@code startUnit} 升序的占用区间（调用方保证有序）
     */
    public static boolean intersectsAny(List<OccupiedLeaveInterval> sortedByStart, LeaveUnitRange target) {
        if (!target.valid() || sortedByStart == null || sortedByStart.isEmpty()) {
            return false;
        }
        int n = sortedByStart.size();
        long[] starts = new long[n];
        long[] maxEndPrefix = new long[n];
        long running = Long.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            LeaveUnitRange range = sortedByStart.get(i).range();
            starts[i] = range.startUnit();
            running = Math.max(running, range.endUnit());
            maxEndPrefix[i] = running;
        }
        // 首个 start > target.end 的下标：其左侧 [0, count) 全部满足 start ≤ target.end
        int count = upperBound(starts, target.endUnit());
        return count > 0 && maxEndPrefix[count - 1] >= target.startUnit();
    }

    /**
     * 首个与 target 相交的占用区间（精确，O(k)）。
     * <p>
     * 顺序语义对齐 Mock {@code findOverlap}（按集合顺序取首个命中）；服务层以「数据库候选（同员工 +
     * 占用态 + 日级相交）按 id 升序」传入，即可与 Mock 的重叠提示单完全一致。
     */
    public static OccupiedLeaveInterval firstIntersecting(Iterable<OccupiedLeaveInterval> inOrder, LeaveUnitRange target) {
        if (!target.valid() || inOrder == null) {
            return null;
        }
        for (OccupiedLeaveInterval candidate : inOrder) {
            if (intersects(candidate.range(), target)) {
                return candidate;
            }
        }
        return null;
    }

    // ==================== 账期跨度 ====================

    /** 请假区间覆盖到的账期（yyyy-MM，升序，连续）。跨月单得到 2 个月，撤回时逐月检查账期锁 */
    public static List<String> monthSpans(LeaveUnitRange range) {
        List<String> months = new ArrayList<>();
        if (!range.valid()) {
            return months;
        }
        YearMonth from = YearMonth.from(dateOfUnit(range.startUnit()));
        YearMonth to = YearMonth.from(dateOfUnit(range.endUnit()));
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.add(month.toString());
        }
        return months;
    }

    // ==================== 二分（插入点语义，对齐 Java Arrays.binarySearch 的 lower/upper） ====================

    /** 首个 ≥ x 的下标（lower_bound） */
    public static int lowerBound(long[] arr, long x) {
        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] < x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** 首个 &gt; x 的下标（upper_bound） */
    public static int upperBound(long[] arr, long x) {
        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
