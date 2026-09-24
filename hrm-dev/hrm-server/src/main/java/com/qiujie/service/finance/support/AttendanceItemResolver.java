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
 */
@Component
@RequiredArgsConstructor
public class AttendanceItemResolver implements PayrollItemResolver {

    private static final Map<String, String> METRIC_LABELS = Map.of(
            "LATE", "迟到", "EARLY_LEAVE", "早退", "ABSENT", "缺勤", "ABNORMAL", "异常卡", "LEAVE", "请假");

    private final AlgoProperties algoProperties;

    @Override
    public String source() {
        return PayrollSource.ATTENDANCE.name();
    }

    @Override
    public PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx) {
        PayrollItemParamAccessor accessor = new PayrollItemParamAccessor(params);
        String metric = accessor.string("metric");
        String field = algoProperties.getPayroll().getAttendanceFieldMap().get(metric);
        AttendanceStat attendance = ctx == null ? null : ctx.attendance();
        BigDecimal count = attendance == null ? BigDecimal.ZERO : attendance.byField(field);
        String label = metric == null ? null : METRIC_LABELS.getOrDefault(metric, metric);

        if ("BONUS_IF_ZERO".equals(accessor.string("mode"))) {
            boolean zero = count.signum() == 0;
            BigDecimal amount = zero ? accessor.numberOrZero("amount") : BigDecimal.ZERO;
            String condition = zero ? "满足发放条件" : "不满足发放条件";
            return new PayrollItemAmount(amount, label + " " + PayrollNumberFormat.plain(count) + " 次，" + condition);
        }

        BigDecimal perUnit = accessor.numberOrZero("amount");
        BigDecimal amount = perUnit.multiply(count);
        String detail = label + " " + PayrollNumberFormat.plain(count) + " 次 × "
                + PayrollNumberFormat.plain(perUnit) + " 元";
        BigDecimal cap = accessor.number("cap");
        if (cap != null && cap.signum() > 0 && amount.compareTo(cap) > 0) {
            amount = cap;
            detail = detail + "，封顶 " + PayrollNumberFormat.plain(cap) + " 元";
        }
        return new PayrollItemAmount(amount, detail);
    }
}
