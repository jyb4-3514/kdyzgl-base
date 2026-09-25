package com.qiujie.service.finance.support;

import com.qiujie.config.AlgoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ATTENDANCE 来源解析器：按考勤统计推算（对齐 Mock {@code attendanceAmount}）。
 * <p>
 * 支持的 {@code params}：{@code metric}（迟到/早退/缺勤/异常卡/请假）、{@code mode}、{@code amount}、{@code cap}。
 * <ul>
 *   <li>{@code PER_COUNT}：{@code amount = count × 单价}，当 {@code cap > 0} 且超限时封顶（{@code cap<=0} 表示不封顶）；</li>
 *   <li>{@code BONUS_IF_ZERO}：{@code count == 0} 才发放 {@code amount}，否则 0。</li>
 * </ul>
 * 指标 → 统计字段的映射走 {@code hrm.algo.payroll.attendanceFieldMap}（表驱动，换映射不改解析器）；
 * {@code cap<=0} 的语义由 {@code itemCapSemantics} 约定（默认 ZERO_MEANS_NO_CAP，与 Mock 一致）。
 * <p>
 * <b>S2b 班次制三处收口</b>：
 * <ol>
 *   <li><b>T1 全勤奖指标</b>：{@code BONUS_IF_ZERO}（全勤奖语义）统一取 {@code hrm.algo.payroll.fullAttendMetric}
 *       （默认 {@code ABSENT_OR_LEAVE} = 旷工 + 请假班次，即请假算缺勤），不再看规则项 {@code params.metric}，
 *       避免「规则项一份 metric、口径一份配置」双真源漂移；</li>
 *   <li><b>封顶链级 1</b>：规则项 {@code params.cap} 仍在解析器内先施加（最具体，先施加）；</li>
 *   <li><b>封顶链级 2</b>：仅缺勤罚款（{@code metric=ABSENT}）追加配置级比例封顶
 *       {@code absentFineCapRatio × 折算后基本工资}，与级 1 逐级取更严（min），不会重复施加（方案 §7.1）。</li>
 * </ol>
 * {@code absentFinePerShift} / {@code absentFineCap} 为规则项未声明 {@code amount} / {@code cap} 时的缺勤罚款默认值
 * （方案 §7：实际以规则项 {@code params.amount} 为准）。
 */
@Component
@RequiredArgsConstructor
public class AttendanceItemResolver implements PayrollItemResolver {

    private static final String METRIC_ABSENT = "ABSENT";
    private static final String MODE_BONUS_IF_ZERO = "BONUS_IF_ZERO";

    private static final Map<String, String> METRIC_LABELS = Map.of(
            "LATE", "迟到", "EARLY_LEAVE", "早退", "ABSENT", "缺勤", "ABNORMAL", "异常卡", "LEAVE", "请假",
            // T1：请假算缺勤，故组合指标沿用「缺勤」文案，员工端可解释性一致
            "ABSENT_OR_LEAVE", "缺勤");

    private final AlgoProperties algoProperties;

    @Override
    public String source() {
        return PayrollSource.ATTENDANCE.name();
    }

    @Override
    public PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemParamAccessor accessor = new PayrollItemParamAccessor(params);
        boolean bonusIfZero = MODE_BONUS_IF_ZERO.equals(accessor.string("mode"));
        String metric = bonusIfZero ? fullAttendMetric(accessor) : accessor.string("metric");
        String field = algoProperties.getPayroll().getAttendanceFieldMap().get(metric);
        AttendanceStat attendance = ctx == null ? null : ctx.attendance();
        BigDecimal count = attendance == null ? BigDecimal.ZERO : attendance.byField(field);
        String label = metric == null ? null : METRIC_LABELS.getOrDefault(metric, metric);

        if (bonusIfZero) {
            boolean zero = count.signum() == 0;
            BigDecimal amount = zero ? accessor.numberOrZero("amount") : BigDecimal.ZERO;
            String condition = zero ? "满足发放条件" : "不满足发放条件";
            return new PayrollItemAmount(amount, label + " " + PayrollNumberFormat.plain(count) + " 次，" + condition);
        }

        BigDecimal perUnit = resolvePerUnit(accessor, metric);
        BigDecimal amount = perUnit.multiply(count);
        // TODO(扩展): 方案 §9.3 建议项 S2 拟把明细单位「次」改「班次」；因解析器不感知账期（旧按天路径下缺勤为「天」），
        //   统一改「班次」会误标历史账期，暂保留「次」，待引入账期/单位维度后再改。
        String detail = label + " " + PayrollNumberFormat.plain(count) + " 次 × "
                + PayrollNumberFormat.plain(perUnit) + " 元";
        // 封顶链级 1：规则项绝对额封顶（最具体，先施加）
        BigDecimal itemCap = resolveItemCap(accessor, metric);
        if (itemCap != null && itemCap.signum() > 0 && amount.compareTo(itemCap) > 0) {
            amount = itemCap;
            detail = detail + "，封顶 " + PayrollNumberFormat.plain(itemCap) + " 元";
        }
        // 封顶链级 2：配置级比例封顶（仅缺勤罚款），在级 1 结果上再取更严
        BigDecimal ratioCap = absentRatioCap(metric, attendance, ctx);
        if (ratioCap != null && amount.compareTo(ratioCap) > 0) {
            amount = ratioCap;
            detail = detail + "，比例封顶 " + PayrollNumberFormat.plain(ratioCap) + " 元";
        }
        return new PayrollItemAmount(amount, detail);
    }

    /** 单价：规则项未声明 amount 且为缺勤罚款时回落 {@code hrm.algo.payroll.absentFinePerShift}（默认 100 元/班次） */
    private BigDecimal resolvePerUnit(PayrollItemParamAccessor accessor, String metric) {
        if (METRIC_ABSENT.equals(metric) && !accessor.has("amount")) {
            BigDecimal fallback = algoProperties.getPayroll().getAbsentFinePerShift();
            return fallback == null ? BigDecimal.ZERO : fallback;
        }
        return accessor.numberOrZero("amount");
    }

    /** 绝对额封顶：规则项未声明 cap 且为缺勤罚款时回落 {@code hrm.algo.payroll.absentFineCap}（默认 0 = 不封顶） */
    private BigDecimal resolveItemCap(PayrollItemParamAccessor accessor, String metric) {
        if (METRIC_ABSENT.equals(metric) && !accessor.has("cap")) {
            return algoProperties.getPayroll().getAbsentFineCap();
        }
        return accessor.number("cap");
    }

    /**
     * 封顶链级 2：缺勤罚款 ≤ 折算后基本工资 × {@code absentFineCapRatio}；系数 &le; 0 = 本级不封顶（默认关闭）。
     * <p>
     * 折算后基本工资按 {@code zeroSchedulePolicy} 兜底口径计算（应出为 0 时取全额或 0），与 {@code PRORATED} 同源。
     */
    private BigDecimal absentRatioCap(String metric, AttendanceStat attendance, PayrollCalcContext ctx) {
        BigDecimal ratio = algoProperties.getPayroll().getAbsentFineCapRatio();
        if (!METRIC_ABSENT.equals(metric) || ratio == null || ratio.signum() <= 0 || ctx == null || ctx.salary() == null) {
            return null;
        }
        BigDecimal basic = ctx.salary().getBasicSalary();
        int required = attendance == null ? 0 : attendance.requiredShifts();
        int attended = attendance == null ? 0 : attendance.attendedShifts();
        BigDecimal basicProrated = ShiftPayrollPolicy.prorated(basic, attended, required,
                algoProperties.getPayroll().getZeroSchedulePolicy());
        return ShiftPayrollPolicy.ratioCap(basicProrated, ratio);
    }

    /** 全勤奖指标：配置优先（T1 一律 ABSENT_OR_LEAVE），配置空时回落规则项声明的 metric */
    private String fullAttendMetric(PayrollItemParamAccessor accessor) {
        String metric = algoProperties.getPayroll().getFullAttendMetric();
        if (metric != null && !metric.isBlank()) {
            return metric;
        }
        return accessor.string("metric");
    }
}
