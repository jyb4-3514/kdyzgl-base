package com.qiujie.service.attendance.support;

import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.CheckPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时段解析单测（边界：24:00、非法串 NaN、跨天进位、时段兜底）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendancePeriodResolverTest {

    @Test
    @DisplayName("'HH:mm' → 分钟数；'24:00' 记为 1440；非法串 → NaN")
    void minutesOfDay() {
        assertEquals(480.0, AttendancePeriodResolver.minutesOfDay("08:00"), 1e-9);
        assertEquals(1440.0, AttendancePeriodResolver.minutesOfDay("24:00"), 1e-9);
        assertEquals(0.0, AttendancePeriodResolver.minutesOfDay("00:00"), 1e-9);
        assertTrue(Double.isNaN(AttendancePeriodResolver.minutesOfDay("abc")));
        assertTrue(Double.isNaN(AttendancePeriodResolver.minutesOfDay(null)));
        assertTrue(Double.isNaN(AttendancePeriodResolver.minutesOfDay("8")));
    }

    @Test
    @DisplayName("分钟数 → 'HH:mm'；越界按 00:00 / 24:00 截断（如 07:00-30、23:00+60）")
    void clockOfMinutesClamps() {
        assertEquals("00:00", AttendancePeriodResolver.clockOfMinutes(-30));
        assertEquals("24:00", AttendancePeriodResolver.clockOfMinutes(1440 + 60));
        assertEquals("16:30", AttendancePeriodResolver.clockOfMinutes(990));
    }

    @Test
    @DisplayName("时段兜底：checkPeriods 为空时用「全天班 + 首末派生时间」")
    void resolveFallback() {
        AttendanceRule rule = new AttendanceRule();
        rule.setWorkStartTime("08:00");
        rule.setWorkEndTime("18:00");
        List<AttendancePeriodResolver.ResolvedPeriod> periods = AttendancePeriodResolver.resolve(rule);
        assertEquals(1, periods.size());
        assertEquals(AttendanceConstants.DEFAULT_PERIOD_NAME, periods.get(0).name());
        assertEquals(480.0, AttendancePeriodResolver.minutesOfDay(periods.get(0).startTime()), 1e-9);
    }

    @Test
    @DisplayName("时段优先于派生值：checkPeriods 存在时按声明解析并带序号")
    void resolveDeclared() {
        AttendanceRule rule = new AttendanceRule();
        rule.setCheckPeriods(List.of(period("上午班", "08:00", "12:00"), period("下午班", "14:00", "18:00")));
        List<AttendancePeriodResolver.ResolvedPeriod> periods = AttendancePeriodResolver.resolve(rule);
        assertEquals(2, periods.size());
        assertEquals(1, periods.get(1).periodIndex());
        assertEquals("下午班", periods.get(1).name());
    }

    @Test
    @DisplayName("跨天：'24:00' 与溢出分钟进位次日（本地时区）")
    void crossDay() {
        LocalDate workDate = LocalDate.of(2026, 9, 24);
        // 1440 分钟 = 24:00 → 次日 00:00
        assertEquals(LocalDateTime.of(2026, 9, 25, 0, 0), AttendancePeriodResolver.at(workDate, 1440));
        // 1450 分钟 = 24:10 → 次日 00:10
        assertEquals(LocalDateTime.of(2026, 9, 25, 0, 10), AttendancePeriodResolver.at(workDate, 1440 + 10));
    }

    private CheckPeriod period(String name, String start, String end) {
        CheckPeriod checkPeriod = new CheckPeriod();
        checkPeriod.setName(name);
        checkPeriod.setStartTime(start);
        checkPeriod.setEndTime(end);
        return checkPeriod;
    }
}
