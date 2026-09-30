package com.qiujie.service.finance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 到点判定纯逻辑单测（算法 v1.3 §1.2~§1.4 / §3 边界用例）。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollSchedulePlannerTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime TEN_THIRTY = LocalTime.of(10, 30);

    @Test
    @DisplayName("钳位：payroll_day=31 遇 2 月 → 当月最后一天（2026-02-28）")
    void computeDueAt_clampToMonthEnd_february() {
        LocalDateTime dueAt = PayrollSchedulePlanner.computeDueAt(31, NINE, YearMonth.of(2026, 2));
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), dueAt);
    }

    @Test
    @DisplayName("钳位：payroll_day=30 遇 2 月非闰年、31 遇 30 天月、闰年 2/29 正常命中")
    void computeDueAt_clampBoundaries() {
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0),
                PayrollSchedulePlanner.computeDueAt(30, NINE, YearMonth.of(2026, 2)));
        assertEquals(LocalDateTime.of(2026, 4, 30, 9, 0),
                PayrollSchedulePlanner.computeDueAt(31, NINE, YearMonth.of(2026, 4)));
        assertEquals(LocalDateTime.of(2024, 2, 29, 9, 0),
                PayrollSchedulePlanner.computeDueAt(29, NINE, YearMonth.of(2024, 2)));
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0),
                PayrollSchedulePlanner.computeDueAt(29, NINE, YearMonth.of(2026, 2)));
        assertEquals(LocalDateTime.of(2026, 8, 31, 9, 0),
                PayrollSchedulePlanner.computeDueAt(31, NINE, YearMonth.of(2026, 8)));
    }

    @Test
    @DisplayName("尝试钟点：dueAt 当日取 payroll_time；补跑日取 catch-up-time-of-day（空则继承 payroll_time）")
    void attemptTimeOf_dueDayVsCatchUpDay() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        assertEquals(LocalDateTime.of(2026, 9, 15, 9, 0),
                PayrollSchedulePlanner.attemptTimeOf(LocalDate.of(2026, 9, 15), dueAt, NINE, TEN_THIRTY));
        assertEquals(LocalDateTime.of(2026, 9, 16, 10, 30),
                PayrollSchedulePlanner.attemptTimeOf(LocalDate.of(2026, 9, 16), dueAt, NINE, TEN_THIRTY));
        // catch-up-time-of-day 为空 → 继承 payroll_time（默认行为与正常到点一致）
        assertEquals(LocalDateTime.of(2026, 9, 16, 9, 0),
                PayrollSchedulePlanner.attemptTimeOf(LocalDate.of(2026, 9, 16), dueAt, NINE, null));
    }

    @Test
    @DisplayName("触发类型：与 dueAt 同日 → AUTO；晚于 dueAt 所在日 → CATCH_UP")
    void triggerType_dayBased() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        assertEquals(PayrollRunTriggerType.AUTO.name(),
                PayrollSchedulePlanner.triggerType(LocalDate.of(2026, 9, 15), dueAt));
        assertEquals(PayrollRunTriggerType.CATCH_UP.name(),
                PayrollSchedulePlanner.triggerType(LocalDate.of(2026, 9, 16), dueAt));
        // 跨月补跑仍以「晚于 dueAt 日」判 CATCH_UP
        assertEquals(PayrollRunTriggerType.CATCH_UP.name(),
                PayrollSchedulePlanner.triggerType(LocalDate.of(2026, 10, 1), dueAt));
    }

    @Test
    @DisplayName("参数校验：算薪日 1..31、时间 HH:mm（含 00:00/23:59 合法，9:00/24:00/09:60 非法）")
    void validity() {
        assertTrue(PayrollSchedulePlanner.isValidDay(1));
        assertTrue(PayrollSchedulePlanner.isValidDay(31));
        assertFalse(PayrollSchedulePlanner.isValidDay(0));
        assertFalse(PayrollSchedulePlanner.isValidDay(32));
        assertFalse(PayrollSchedulePlanner.isValidDay(null));

        assertTrue(PayrollSchedulePlanner.isValidTime("00:00"));
        assertTrue(PayrollSchedulePlanner.isValidTime("23:59"));
        assertFalse(PayrollSchedulePlanner.isValidTime("9:00"));
        assertFalse(PayrollSchedulePlanner.isValidTime("24:00"));
        assertFalse(PayrollSchedulePlanner.isValidTime("09:60"));
        assertFalse(PayrollSchedulePlanner.isValidTime(""));
        assertFalse(PayrollSchedulePlanner.isValidTime(null));

        assertTrue(PayrollSchedulePlanner.isConfigValid(5, "09:00"));
        assertFalse(PayrollSchedulePlanner.isConfigValid(32, "09:00"));
        assertFalse(PayrollSchedulePlanner.isConfigValid(5, "9:00"));
    }

    @Test
    @DisplayName("时间解析：合法返回 LocalTime；非法/空返回 null（不抛异常污染调度线程）")
    void parseTimeOrNull() {
        assertEquals(LocalTime.of(9, 0), PayrollSchedulePlanner.parseTimeOrNull("09:00"));
        assertNull(PayrollSchedulePlanner.parseTimeOrNull("9:00"));
        assertNull(PayrollSchedulePlanner.parseTimeOrNull(""));
        assertNull(PayrollSchedulePlanner.parseTimeOrNull(null));
    }
}
