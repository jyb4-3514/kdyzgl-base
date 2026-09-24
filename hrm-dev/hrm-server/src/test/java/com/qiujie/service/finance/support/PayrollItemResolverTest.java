package com.qiujie.service.finance.support;

import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.HrAllowance;
import com.qiujie.entity.HrSalary;
import com.qiujie.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四类来源解析器 + 注册表纯逻辑单测（对齐 Mock {@code resolveItem} 与算法 S2 原型
 * {@code algo-scripts/s2-payroll.mjs}）。
 * <p>
 * 边界覆盖：无定薪档案 / 无绩效基数 / 无 KPI 记录 / 未知 source / cap=0·负·缺失 / count=0 /
 * capRatio=0·负·缺失 / 指定津贴项命中与未命中。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行（与 S2 原型逐例对照）。</p>
 */
class PayrollItemResolverTest {

    private final AlgoProperties algo = new AlgoProperties();

    private final FixedItemResolver fixed = new FixedItemResolver();
    private final AttendanceItemResolver attendance = new AttendanceItemResolver(algo);
    private final KpiItemResolver kpi = new KpiItemResolver(algo);
    private final ManualItemResolver manual = new ManualItemResolver();

    // ==================== FIXED ====================

    @Test
    @DisplayName("FIXED：无定薪档案 → 0")
    void fixedNoSalary() {
        PayrollItemAmount r = fixed.resolve(Map.of("field", "basicSalary"), PayrollCalcContext.empty());
        assertEquals(0, r.amount().compareTo(BigDecimal.ZERO));
        assertEquals("未设置定薪档案，按 0 计", r.detail());
    }

    @Test
    @DisplayName("FIXED：按字段取数（基本工资）")
    void fixedField() {
        PayrollItemAmount r = fixed.resolve(Map.of("field", "basicSalary"),
                new PayrollCalcContext(salary(new BigDecimal("6000"), new BigDecimal("2500"),
                        new BigDecimal("2000"), allowances(), new BigDecimal("500")), null, null));
        assertEquals(0, r.amount().compareTo(new BigDecimal("6000")));
        assertEquals("取人事定薪项：基本工资", r.detail());
    }

    @Test
    @DisplayName("FIXED：津贴合计（无 allowanceKey）")
    void fixedAllowancesTotal() {
        PayrollItemAmount r = fixed.resolve(Map.of("field", "allowances"),
                new PayrollCalcContext(salary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        allowances(), new BigDecimal("500")), null, null));
        assertEquals(0, r.amount().compareTo(new BigDecimal("500")));
        assertEquals("取人事定薪项：津贴合计（2 项）", r.detail());
    }

    @Test
    @DisplayName("FIXED：指定津贴项命中 / 未命中")
    void fixedAllowanceKey() {
        PayrollCalcContext ctx = new PayrollCalcContext(salary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                allowances(), new BigDecimal("500")), null, null);
        PayrollItemAmount hit = fixed.resolve(Map.of("field", "allowances", "allowanceKey", "MEAL"), ctx);
        assertEquals(0, hit.amount().compareTo(new BigDecimal("300")));
        assertEquals("取人事定薪项：餐补", hit.detail());

        PayrollItemAmount miss = fixed.resolve(Map.of("field", "allowances", "allowanceKey", "GHOST"), ctx);
        assertEquals(0, miss.amount().compareTo(BigDecimal.ZERO));
        assertEquals("取人事定薪项：GHOST", miss.detail());
    }

    // ==================== ATTENDANCE ====================

    @Test
    @DisplayName("ATTENDANCE：BONUS_IF_ZERO（缺勤 0 次发 / 1 次不发）")
    void attendanceBonusIfZero() {
        Map<String, Object> params = Map.of("metric", "ABSENT", "mode", "BONUS_IF_ZERO", "amount", 200);
        PayrollItemAmount zero = attendance.resolve(params, ctx(null, stat(0, 0, 0), null));
        assertEquals(0, zero.amount().compareTo(new BigDecimal("200")));
        assertEquals("缺勤 0 次，满足发放条件", zero.detail());

        PayrollItemAmount one = attendance.resolve(params, ctx(null, stat(0, 0, 1), null));
        assertEquals(0, one.amount().compareTo(BigDecimal.ZERO));
        assertEquals("缺勤 1 次，不满足发放条件", one.detail());
    }

    @Test
    @DisplayName("ATTENDANCE：PER_COUNT 封顶（迟到 20×20 封顶 300）与 cap=0 不封顶（缺勤 10×150=1500）")
    void attendancePerCountCap() {
        PayrollItemAmount capped = attendance.resolve(Map.of("metric", "LATE", "mode", "PER_COUNT", "amount", 20, "cap", 300),
                ctx(null, stat(20, 0, 0), null));
        assertEquals(0, capped.amount().compareTo(new BigDecimal("300")));
        assertEquals("迟到 20 次 × 20 元，封顶 300 元", capped.detail());

        PayrollItemAmount noCap = attendance.resolve(Map.of("metric", "ABSENT", "mode", "PER_COUNT", "amount", 150, "cap", 0),
                ctx(null, stat(0, 0, 10), null));
        assertEquals(0, noCap.amount().compareTo(new BigDecimal("1500")));
        assertEquals("缺勤 10 次 × 150 元", noCap.detail());
    }

    @Test
    @DisplayName("ATTENDANCE：cap 缺失时同样不封顶（Number(undefined)=NaN）")
    void attendanceCapAbsent() {
        PayrollItemAmount r = attendance.resolve(Map.of("metric", "LATE", "mode", "PER_COUNT", "amount", 20),
                ctx(null, stat(20, 0, 0), null));
        assertEquals(0, r.amount().compareTo(new BigDecimal("400")));
    }

    @Test
    @DisplayName("ATTENDANCE：count=0 → 0；无考勤上下文 → 0（不抛错）")
    void attendanceZero() {
        PayrollItemAmount zero = attendance.resolve(Map.of("metric", "LATE", "mode", "PER_COUNT", "amount", 20),
                ctx(null, stat(0, 0, 0), null));
        assertEquals(0, zero.amount().compareTo(BigDecimal.ZERO));
        assertEquals("迟到 0 次 × 20 元", zero.detail());

        PayrollItemAmount noCtx = attendance.resolve(Map.of("metric", "LATE", "mode", "PER_COUNT", "amount", 20),
                PayrollCalcContext.empty());
        assertEquals(0, noCtx.amount().compareTo(BigDecimal.ZERO));
    }

    // ==================== KPI ====================

    @Test
    @DisplayName("KPI：满分（100）→ 2000；得分 130、capRatio=1.2 → 2400（受 120% 上限约束）")
    void kpiCapRatio() {
        PayrollCalcContext ctx100 = ctx(salary(ZERO(), ZERO(), new BigDecimal("2000"), List.of(), ZERO()),
                null, new BigDecimal("100"));
        PayrollItemAmount full = kpi.resolve(Map.of("baseField", "performanceBase", "capRatio", 1.2), ctx100);
        assertEquals(0, full.amount().compareTo(new BigDecimal("2000")));
        assertEquals("绩效基数 2000 × KPI 得分 100%（上限 120%） = 2000 元", full.detail());

        PayrollCalcContext ctx130 = ctx(salary(ZERO(), ZERO(), new BigDecimal("2000"), List.of(), ZERO()),
                null, new BigDecimal("130"));
        PayrollItemAmount over = kpi.resolve(Map.of("baseField", "performanceBase", "capRatio", 1.2), ctx130);
        assertEquals(0, over.amount().compareTo(new BigDecimal("2400")));
        assertEquals("绩效基数 2000 × KPI 得分 130%（上限 120%） = 2400 元", over.detail());
    }

    @Test
    @DisplayName("KPI：capRatio 缺失 → 默认 1.0（得分 130 也只发 2000，且无上限文案）")
    void kpiDefaultCapRatio() {
        PayrollCalcContext ctx = ctx(salary(ZERO(), ZERO(), new BigDecimal("2000"), List.of(), ZERO()),
                null, new BigDecimal("130"));
        PayrollItemAmount r = kpi.resolve(Map.of("baseField", "performanceBase"), ctx);
        assertEquals(0, r.amount().compareTo(new BigDecimal("2000")));
        assertTrue(r.detail().contains("绩效基数 2000") && !r.detail().contains("上限"));
    }

    @Test
    @DisplayName("KPI：capRatio=0 / 负 → 生效上限回落 1")
    void kpiInvalidCapRatio() {
        PayrollCalcContext ctx = ctx(salary(ZERO(), ZERO(), new BigDecimal("2000"), List.of(), ZERO()),
                null, new BigDecimal("100"));
        assertEquals(0, kpi.resolve(Map.of("baseField", "performanceBase", "capRatio", 0), ctx).amount()
                .compareTo(new BigDecimal("2000")));
        assertEquals(0, kpi.resolve(Map.of("baseField", "performanceBase", "capRatio", -1), ctx).amount()
                .compareTo(new BigDecimal("2000")));
    }

    @Test
    @DisplayName("KPI：无评分记录 / 缺绩效基数 → 0 且文案区分")
    void kpiDegenerate() {
        PayrollItemAmount noScore = kpi.resolve(Map.of("baseField", "performanceBase"),
                ctx(salary(ZERO(), ZERO(), new BigDecimal("2000"), List.of(), ZERO()), null, null));
        assertEquals("该月无 KPI 评分记录，按 0 计", noScore.detail());

        PayrollItemAmount noBase = kpi.resolve(Map.of("baseField", "performanceBase"),
                ctx(salary(ZERO(), ZERO(), ZERO(), List.of(), ZERO()), null, new BigDecimal("100")));
        assertEquals("定薪档案缺少绩效基数，按 0 计", noBase.detail());
    }

    // ==================== MANUAL ====================

    @Test
    @DisplayName("MANUAL：取 defaultValue；缺失 → 0")
    void manual() {
        assertEquals(0, manual.resolve(Map.of("defaultValue", 500), PayrollCalcContext.empty()).amount()
                .compareTo(new BigDecimal("500")));
        assertEquals(0, manual.resolve(Map.of(), PayrollCalcContext.empty()).amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, manual.resolve(null, PayrollCalcContext.empty()).amount().compareTo(BigDecimal.ZERO));
    }

    // ==================== 注册表 ====================

    @Test
    @DisplayName("注册表：四来源可分发；未知来源按 0 计且不中断")
    void registryDispatchAndUnknown() {
        PayrollResolverRegistry registry = new PayrollResolverRegistry(
                List.of(fixed, attendance, kpi, manual), algo);
        assertEquals(4, registry.registeredSources().size());

        PayrollItemAmount unknown = registry.resolve("GHOST", Map.of(), PayrollCalcContext.empty());
        assertEquals(0, unknown.amount().compareTo(BigDecimal.ZERO));
        assertEquals("未知来源，按 0 计", unknown.detail());

        PayrollItemAmount known = registry.resolve("MANUAL", Map.of("defaultValue", 88), PayrollCalcContext.empty());
        assertEquals(0, known.amount().compareTo(new BigDecimal("88")));
    }

    @Test
    @DisplayName("注册表：THROW 策略下未知来源抛业务异常")
    void registryUnknownThrow() {
        AlgoProperties throwAlgo = new AlgoProperties();
        throwAlgo.getPayroll().setUnknownSourcePolicy("THROW");
        PayrollResolverRegistry registry = new PayrollResolverRegistry(List.of(manual), throwAlgo);
        assertThrows(BusinessException.class,
                () -> registry.resolve("GHOST", Map.of(), PayrollCalcContext.empty()));
    }

    // ==================== 夹具 ====================

    private static BigDecimal ZERO() {
        return BigDecimal.ZERO;
    }

    private static HrSalary salary(BigDecimal basic, BigDecimal post, BigDecimal performance,
                                   List<HrAllowance> allowances, BigDecimal allowancesTotal) {
        HrSalary salary = new HrSalary();
        salary.setBasicSalary(basic);
        salary.setPostSalary(post);
        salary.setPerformanceBase(performance);
        salary.setAllowances(allowances);
        salary.setAllowancesTotal(allowancesTotal);
        return salary;
    }

    private static List<HrAllowance> allowances() {
        return List.of(new HrAllowance("MEAL", "餐补", new BigDecimal("300")),
                new HrAllowance("TRANSPORT", "交通", new BigDecimal("200")));
    }

    private static AttendanceStat stat(int late, int earlyLeave, int absent) {
        // absentCount / leaveCount 为 BigDecimal（P7 请假按半天粒度减除缺勤）；此处仅做夹具类型适配，断言不变
        return new AttendanceStat(late, earlyLeave, BigDecimal.valueOf(absent), 0, BigDecimal.ZERO);
    }

    private static PayrollCalcContext ctx(HrSalary salary, AttendanceStat attendance, BigDecimal kpiScore) {
        return new PayrollCalcContext(salary, attendance, kpiScore);
    }
}
