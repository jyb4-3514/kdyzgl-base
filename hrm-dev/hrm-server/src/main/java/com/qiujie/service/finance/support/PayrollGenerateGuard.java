package com.qiujie.service.finance.support;

import com.qiujie.entity.Payroll;

import java.util.List;
import java.util.Set;

/**
 * 生成幂等判定（纯逻辑，对齐 Mock {@code generatePayrolls}）。
 * <p>
 * 口径：同月同员工同类型（MONTHLY）已存在 DRAFT / REJECTED（「可覆盖」）→ <b>覆盖重建</b>；
 * 若该月已存在任何非 DRAFT/REJECTED 的月度单 → <b>整批拒绝（9405）</b>，
 * 避免把老板已审过、员工已看过的工资单悄悄改掉。
 * <p>
 * <b>驿站收敛（C-7 必改 1）</b>：判定可选按「目标驿站集合」收敛——否则多驿站自动算薪时，
 * A 站先跑出的非可覆盖单会把 B 站整批阻断，只能成功第一个站点。
 */
public final class PayrollGenerateGuard {

    private PayrollGenerateGuard() {
    }

    /** 可覆盖状态（覆盖重建范围）：DRAFT / REJECTED */
    public static List<String> editableStatuses() {
        return List.of(PayrollStatus.DRAFT.name(), PayrollStatus.REJECTED.name());
    }

    /** 单据状态是否可被重复生成覆盖 */
    public static boolean isEditable(String status) {
        return PayrollStateMachine.isOverwritable(status);
    }

    /**
     * 从全部工资单中找「阻断该月生成」的单据（同月 + MONTHLY + 非可覆盖），无则返回 null。
     * 供 9405 文案回填单据号。测试与 Service 共用同一判定。
     * <p>不做驿站收敛（全站参与），供无驿站语境的判定与既有调用点使用。
     */
    public static Payroll findBlockingMonthly(List<Payroll> payrolls, String month) {
        return findBlockingMonthly(payrolls, month, null);
    }

    /**
     * 按目标驿站集合收敛的阻断判定（C-7）。
     *
     * @param stationIds 目标驿站集合；{@code null} = 不收敛（全站参与判定）
     * @return 命中的阻断单据；无则 null
     */
    public static Payroll findBlockingMonthly(List<Payroll> payrolls, String month, Set<Long> stationIds) {
        if (payrolls == null || month == null) {
            return null;
        }
        for (Payroll payroll : payrolls) {
            if (payroll == null) {
                continue;
            }
            if (!PayrollBillType.MONTHLY.name().equals(payroll.getBillType())) {
                continue;
            }
            if (!month.equals(payroll.getMonth())) {
                continue;
            }
            if (!isEditable(payroll.getStatus()) && stationMatches(payroll, stationIds)) {
                return payroll;
            }
        }
        return null;
    }

    /**
     * 单据是否归属目标驿站集合。
     * <p>为什么无归属（{@code stationId == null}）一律命中：无从判定归属即保守参与阻断，宁可多拦不可漏拦，
     * 避免历史脏数据（未记归属）绕过多驿站收敛而丢失 9405 保护。
     */
    private static boolean stationMatches(Payroll payroll, Set<Long> stationIds) {
        if (stationIds == null) {
            return true;
        }
        return payroll.getStationId() == null || stationIds.contains(payroll.getStationId());
    }
}
