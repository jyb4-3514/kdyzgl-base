package com.qiujie.service.finance.support;

/**
 * 工资单号生成（纯逻辑，对齐 Mock {@code buildPayroll}）。
 * <p>
 * 规则：{@code 前缀-账期(去横线)-员工id(左补零至4位)}；月度 {@code PAY}、结算 {@code SET}。
 * 例如：员工 12、账期 2026-09 → {@code PAY-202609-0012}。
 */
public final class PayrollNoGenerator {

    private static final String PREFIX_MONTHLY = "PAY";
    private static final String PREFIX_SETTLEMENT = "SET";

    private PayrollNoGenerator() {
    }

    /**
     * @param billType   MONTHLY / SETTLEMENT
     * @param month      账期 yyyy-MM
     * @param employeeId 员工 id
     */
    public static String of(String billType, String month, Long employeeId) {
        String prefix = PayrollBillType.SETTLEMENT.name().equals(billType) ? PREFIX_SETTLEMENT : PREFIX_MONTHLY;
        String monthPart = month == null ? "" : month.replace("-", "");
        // %04d 左补零至 4 位，超 4 位不截断（对齐 Mock padStart(4,'0')）
        return prefix + "-" + monthPart + "-" + String.format("%04d", employeeId == null ? 0L : employeeId);
    }
}
