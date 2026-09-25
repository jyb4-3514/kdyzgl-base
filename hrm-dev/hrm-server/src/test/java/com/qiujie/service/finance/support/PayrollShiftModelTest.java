package com.qiujie.service.finance.support;

import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.HrSalary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2b 班次制算薪单测（唯一真源 {@code hrm-dev/docs/algo-payroll-shift.md} v2.0；对照原型 {@code algo-scripts/s2b-payroll-shift.mjs}）。
 * <p>
 * 覆盖任务验收四项：① 5 个验收数字逐位一致（1500/1475/1375/1450/1250）；② 边界用例（空排班 / 无打卡封顶到 0 /
 * 整月全假 / 跨日单 / 空 period_name 双班不克扣 / 单班制历史 ratio=1）；③ 缺勤恒等式（应出 = 实出 + 请假 + 旷工，批量 0 违例）；
 * ④ 幂等（同一输入重复计算结果逐位一致）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行（命令见变更回报）。</p>
 */
class PayrollShiftModelTest {

    private static final BigDecimal BASIC = new BigDecimal("1500");
    private static final int MIDDAY_BOUNDARY_MINUTE = 720;
    private static final String SENTINEL = "全天班";

    private final AlgoProperties algo = new AlgoProperties();
    private final ProratedItemResolver prorated = new ProratedItemResolver(algo);
    private final AttendanceItemResolver attendance = new AttendanceItemResolver(algo);

    // ==================== 验收 ①：5 个验收数字 ====================

    @Test
    @DisplayName("验收：全勤 1500 / 请半天假 1475 / 旷工半天 1375 / 请全天假 1450 / 旷工全天 1250")
    void acceptanceNumbers() {
        List<ShiftPayrollPolicy.ScheduleRow> schedules = fullMonthSchedules();
        Set<Long> all = allKeys();

        assertEquals(0, calc(schedules, recordsOf(all), Set.of()).net().compareTo(new BigDecimal("1500.00")));

        Set<Long> halfDay = keysOf(Set.of("2026-10-01#1"));
        assertEquals(0, calc(schedules, recordsOf(drop(all, halfDay)), leaveUnits("2026-10-01", 1, "2026-10-01", 1))
                .net().compareTo(new BigDecimal("1475.00")));

        assertEquals(0, calc(schedules, recordsOf(drop(all, halfDay)), Set.of())
                .net().compareTo(new BigDecimal("1375.00")));

        Set<Long> fullDay = keysOf(Set.of("2026-10-01#0", "2026-10-01#1"));
        assertEquals(0, calc(schedules, recordsOf(drop(all, fullDay)), leaveUnits("2026-10-01", 0, "2026-10-01", 1))
                .net().compareTo(new BigDecimal("1450.00")));

        assertEquals(0, calc(schedules, recordsOf(drop(all, fullDay)), Set.of())
                .net().compareTo(new BigDecimal("1250.00")));
    }

    // ==================== 验收 ②：边界与降级 ====================

    @Test
    @DisplayName("边界：空排班 → FULL_BASIC 不克扣（1500，应出 0）")
    void emptySchedule() {
        Calc calc = calc(List.of(), List.of(), Set.of());
        assertEquals(0, calc.result().requiredShifts());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1500.00")));
    }

    @Test
    @DisplayName("边界：整月无打卡 → 折算 0、罚款 6000，封顶后实发 0（非负）")
    void noPunchClampedToZero() {
        Calc calc = calc(fullMonthSchedules(), List.of(), Set.of());
        assertEquals(60, calc.result().requiredShifts());
        assertEquals(60, calc.result().absentShifts());
        assertEquals(0, calc.prorated().compareTo(BigDecimal.ZERO));
        assertEquals(0, calc.fine().compareTo(new BigDecimal("6000.00")));
        assertEquals(0, calc.net().compareTo(BigDecimal.ZERO));
        assertFalse(calc.net().signum() < 0);
    }

    @Test
    @DisplayName("边界：整月全假 → 折算 0、旷工 0、罚款 0、实发 0")
    void fullMonthLeave() {
        Calc calc = calc(fullMonthSchedules(), List.of(), leaveUnits("2026-10-01", 0, "2026-10-30", 1));
        assertEquals(60, calc.result().leaveShifts());
        assertEquals(0, calc.result().absentShifts());
        assertEquals(0, calc.fine().compareTo(BigDecimal.ZERO));
        assertEquals(0, calc.net().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("边界：跨日单 PM→AM 覆盖中间整日（实出 56 / 请假 4 / 1400）")
    void crossDayLeave() {
        Set<Long> leave = leaveUnits("2026-10-01", 1, "2026-10-03", 0);
        assertEquals(4, leave.size());
        Calc calc = calc(fullMonthSchedules(), recordsOf(drop(allKeys(), leave)), leave);
        assertEquals(56, calc.result().attendedShifts());
        assertEquals(4, calc.result().leaveShifts());
        assertEquals(0, calc.result().absentShifts());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1400.00")));
    }

    @Test
    @DisplayName("边界：非法区间（同日 PM→AM）→ 0 班次，缺勤照罚（1375）")
    void illegalLeaveRange() {
        Set<Long> illegal = leaveUnits("2026-10-01", 1, "2026-10-01", 0);
        assertTrue(illegal.isEmpty());
        Calc calc = calc(fullMonthSchedules(),
                recordsOf(drop(allKeys(), keysOf(Set.of("2026-10-01#1")))), illegal);
        assertEquals(1, calc.result().absentShifts());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1375.00")));
    }

    @Test
    @DisplayName("边界：ABNORMAL 卡不计出勤 → 记旷工（1375）")
    void abnormalCard() {
        Set<Long> all = allKeys();
        List<ShiftPayrollPolicy.RecordRow> records = new ArrayList<>(recordsOf(drop(all, keysOf(Set.of("2026-10-01#0")))));
        records.add(new ShiftPayrollPolicy.RecordRow(LocalDate.of(2026, 10, 1), 0, "早班", "ON", "ABNORMAL"));
        Calc calc = calc(fullMonthSchedules(), records, Set.of());
        assertEquals(59, calc.result().attendedShifts());
        assertEquals(1, calc.result().absentShifts());
        assertEquals(1, calc.result().abnormalCount());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1375.00")));
    }

    @Test
    @DisplayName("边界 B4：空 period_name + 双班 → 全班次计出勤不克扣（实出 60）+ 告警 30")
    void blankPeriodNameDoubleShift() {
        List<ShiftPayrollPolicy.RecordRow> records = new ArrayList<>();
        for (LocalDate day : monthDays()) {
            records.add(new ShiftPayrollPolicy.RecordRow(day, 0, null, "ON", "NORMAL"));
        }
        Calc calc = calc(fullMonthSchedules(), records, Set.of());
        assertEquals(60, calc.result().attendedShifts());
        assertEquals(0, calc.result().absentShifts());
        assertEquals(30, calc.result().warnings().size());
        assertEquals(ShiftPayrollPolicy.WARN_PERIOD_NAME_MISSING, calc.result().warnings().get(0).reason());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1500.00")));
    }

    @Test
    @DisplayName("边界 B4：空 period_name + 单班 → 归属唯一班次（实出 30，无告警）")
    void blankPeriodNameSingleShift() {
        List<ShiftPayrollPolicy.ScheduleRow> schedules = new ArrayList<>();
        List<ShiftPayrollPolicy.RecordRow> records = new ArrayList<>();
        for (LocalDate day : monthDays()) {
            schedules.add(new ShiftPayrollPolicy.ScheduleRow(day, "08:00"));
            records.add(new ShiftPayrollPolicy.RecordRow(day, 0, "", "ON", "NORMAL"));
        }
        Calc calc = calc(schedules, records, Set.of());
        assertEquals(30, calc.result().attendedShifts());
        assertTrue(calc.result().warnings().isEmpty());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1500.00")));
    }

    @Test
    @DisplayName("边界：单班制历史哨兵「全天班」→ ratio=1 不突变（全勤 1500 / 缺一天 1325）")
    void legacySingleShiftSentinel() {
        List<ShiftPayrollPolicy.ScheduleRow> schedules = new ArrayList<>();
        for (LocalDate day : monthDays().subList(0, 20)) {
            schedules.add(new ShiftPayrollPolicy.ScheduleRow(day, "08:00"));
        }
        List<ShiftPayrollPolicy.RecordRow> full = new ArrayList<>();
        for (LocalDate day : monthDays().subList(0, 20)) {
            full.add(new ShiftPayrollPolicy.RecordRow(day, 0, SENTINEL, "ON", "NORMAL"));
        }
        Calc ok = calc(schedules, full, Set.of());
        assertEquals(20, ok.result().requiredShifts());
        assertEquals(20, ok.result().attendedShifts());
        assertEquals(0, ok.net().compareTo(new BigDecimal("1500.00")));

        Calc gap = calc(schedules, full.subList(0, 19), Set.of());
        assertEquals(19, gap.result().attendedShifts());
        assertEquals(1, gap.result().absentShifts());
        assertEquals(0, gap.net().compareTo(new BigDecimal("1325.00")));
    }

    @Test
    @DisplayName("边界：请假与出勤重叠 → 集合去重不双扣（实出 60 / 旷工 0）")
    void leaveOverlapsAttendance() {
        Calc calc = calc(fullMonthSchedules(), recordsOf(allKeys()),
                leaveUnits("2026-10-01", 1, "2026-10-01", 1));
        assertEquals(60, calc.result().attendedShifts());
        assertEquals(1, calc.result().leaveShifts());
        assertEquals(0, calc.result().absentShifts());
        assertEquals(0, calc.net().compareTo(new BigDecimal("1500.00")));
    }

    // ==================== 验收 ③：缺勤恒等式（批量随机数据 0 违例） ====================

    @Test
    @DisplayName("恒等式：应出 = 实出 + 请假 + 旷工（500 人 × 满月，0 违例）")
    void identityOverRandomBatch() {
        Random random = new Random(20260925L);
        int violations = 0;
        for (int employee = 0; employee < 500; employee++) {
            List<ShiftPayrollPolicy.ScheduleRow> schedules = new ArrayList<>();
            List<ShiftPayrollPolicy.RecordRow> records = new ArrayList<>();
            Set<Long> leaveUnits = new HashSet<>();
            for (LocalDate day : monthDays()) {
                if (random.nextDouble() < 1.0 / 6) {
                    continue; // 轮休：当日无排班
                }
                for (int ordinal = 0; ordinal < 2; ordinal++) {
                    String startTime = ordinal == 0 ? "08:00" : "16:00";
                    schedules.add(new ShiftPayrollPolicy.ScheduleRow(day, startTime));
                    if (random.nextDouble() < 0.04) {
                        if (random.nextDouble() < 0.4) {
                            leaveUnits.add(ShiftPayrollPolicy.shiftUnit(day, ordinal)); // 已批请假覆盖
                        }
                        continue; // 未到且无假 = 旷工
                    }
                    records.add(new ShiftPayrollPolicy.RecordRow(day, ordinal,
                            ordinal == 0 ? "早班" : "晚班", "ON", "NORMAL"));
                }
            }
            ShiftPayrollPolicy.Result result = ShiftPayrollPolicy.compute(schedules, records, leaveUnits,
                    MIDDAY_BOUNDARY_MINUTE, SENTINEL, ShiftPayrollPolicy.LATE_PER_CARD);
            boolean bad = result.absentShifts() < 0
                    || result.attendedShifts() > result.requiredShifts()
                    || result.requiredShifts() != result.attendedShifts() + result.leaveShifts() + result.absentShifts();
            if (bad) {
                violations++;
            }
        }
        assertEquals(0, violations, "恒等式违例数应为 0");
    }

    // ==================== 验收 ④：幂等 ====================

    @Test
    @DisplayName("幂等：同一输入重复计算，结果与金额逐位一致")
    void idempotent() {
        List<ShiftPayrollPolicy.ScheduleRow> schedules = fullMonthSchedules();
        List<ShiftPayrollPolicy.RecordRow> records = recordsOf(drop(allKeys(), keysOf(Set.of("2026-10-01#1"))));
        Set<Long> leave = leaveUnits("2026-10-01", 1, "2026-10-01", 1);

        Calc first = calc(schedules, records, leave);
        Calc second = calc(schedules, records, leave);
        assertEquals(first.result(), second.result());
        assertEquals(0, first.net().compareTo(second.net()));
        assertEquals(0, first.prorated().compareTo(second.prorated()));
        assertEquals(0, first.fine().compareTo(second.fine()));
    }

    // ==================== 计算夹具 ====================

    private Calc calc(List<ShiftPayrollPolicy.ScheduleRow> schedules,
                      List<ShiftPayrollPolicy.RecordRow> records,
                      Set<Long> leaveUnits) {
        ShiftPayrollPolicy.Result result = ShiftPayrollPolicy.compute(schedules, records, leaveUnits,
                MIDDAY_BOUNDARY_MINUTE, SENTINEL, ShiftPayrollPolicy.LATE_PER_CARD);
        AttendanceStat stat = AttendanceStat.ofShift(result);
        HrSalary salary = new HrSalary();
        salary.setBasicSalary(BASIC);
        PayrollCalcContext ctx = new PayrollCalcContext(salary, stat, null);

        BigDecimal basicPart = prorated.resolve(Map.of("field", "basicSalary"), ctx).amount();
        BigDecimal fine = attendance.resolve(
                Map.of("metric", "ABSENT", "mode", "PER_COUNT", "amount", 100, "cap", 0), ctx).amount();
        PayrollTotals totals = PayrollTotalsPolicy.of(List.of(
                new PayrollItemDraft("BASIC", "基本工资", "ADDITION", "PRORATED", basicPart, "测试"),
                new PayrollItemDraft("ABSENT_FINE", "缺勤扣款", "DEDUCTION", "ATTENDANCE", fine, "测试")),
                false);
        return new Calc(result, basicPart, fine, totals.netAmount());
    }

    private record Calc(ShiftPayrollPolicy.Result result, BigDecimal prorated, BigDecimal fine, BigDecimal net) {
    }

    private static List<LocalDate> monthDays() {
        List<LocalDate> days = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            days.add(LocalDate.of(2026, 10, i));
        }
        return days;
    }

    private static List<ShiftPayrollPolicy.ScheduleRow> fullMonthSchedules() {
        List<ShiftPayrollPolicy.ScheduleRow> schedules = new ArrayList<>();
        for (LocalDate day : monthDays()) {
            schedules.add(new ShiftPayrollPolicy.ScheduleRow(day, "08:00"));
            schedules.add(new ShiftPayrollPolicy.ScheduleRow(day, "16:00"));
        }
        return schedules;
    }

    /** 出勤班次键 → ON 打卡记录（periodName 非空，走三态优先级③） */
    private static List<ShiftPayrollPolicy.RecordRow> recordsOf(Set<Long> keys) {
        List<ShiftPayrollPolicy.RecordRow> records = new ArrayList<>();
        for (Long key : keys) {
            int ordinal = (int) (key & 1L);
            records.add(new ShiftPayrollPolicy.RecordRow(dayOfUnit(key), ordinal,
                    ordinal == 0 ? "早班" : "晚班", "ON", "NORMAL"));
        }
        return records;
    }

    /** 满月双班的全部班次键（日 10-01..10-30 × 早/晚） */
    private static Set<Long> allKeys() {
        Set<Long> keys = new HashSet<>();
        for (LocalDate day : monthDays()) {
            keys.add(ShiftPayrollPolicy.shiftUnit(day, 0));
            keys.add(ShiftPayrollPolicy.shiftUnit(day, 1));
        }
        return keys;
    }

    /** 形如 {@code 2026-10-01#1} 的键集合 → 班次单元集合 */
    private static Set<Long> keysOf(Set<String> textKeys) {
        Set<Long> keys = new HashSet<>();
        for (String text : textKeys) {
            String[] parts = text.split("#");
            LocalDate date = LocalDate.parse(parts[0]);
            keys.add(ShiftPayrollPolicy.shiftUnit(date, Integer.parseInt(parts[1])));
        }
        return keys;
    }

    private static Set<Long> drop(Set<Long> keys, Set<Long> removed) {
        Set<Long> rest = new HashSet<>(keys);
        rest.removeAll(removed);
        return rest;
    }

    /** 请假区间（起日+起序号 → 止日+止序号）展开为班次单元集合；止 < 起 = 非法区间 → 空集 */
    private static Set<Long> leaveUnits(String startDate, int startOrdinal, String endDate, int endOrdinal) {
        long start = ShiftPayrollPolicy.shiftUnit(LocalDate.parse(startDate), startOrdinal);
        long end = ShiftPayrollPolicy.shiftUnit(LocalDate.parse(endDate), endOrdinal);
        Set<Long> units = new HashSet<>();
        for (long unit = start; unit <= end; unit++) {
            units.add(unit);
        }
        return units;
    }

    private static LocalDate dayOfUnit(long unit) {
        return LocalDate.ofEpochDay(Math.floorDiv(unit, 2L));
    }
}
