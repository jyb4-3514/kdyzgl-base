package com.qiujie.service.finance.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 跨域只读端口：已批请假天数与扣款开关（财务域 ← 请假域，架构 §2.2「只读接口」）。
 * <p>
 * <b>为什么由财务域声明接口、请假域提供实现（依赖倒置）</b>：请假域已依赖财务域的
 * {@code PayrollLockQueryService}（撤回账期锁），若财务域再直接 import 请假域 Service 即形成
 * {@code leave ↔ finance} 双向依赖。故由消费方（财务）声明端口，提供方（请假）实现，
 * 依赖方向恒为 {@code leave → finance}（实现方依赖接口方），断环成立（与 P5 人事 / P6 财务的
 * {@code PayrollSettlementPort} 同一手法）。
 * <p>
 * <b>用途</b>：P6 算薪侧接入「已批请假天数」并按 {@code leaveDeductEnabled} 修正
 * {@code absentCount}（替换 P6 预留的 {@code TODO(扩展)}）。跨账期由调用方按月区间调用天然切分，
 * 不需要在一处做整单归属判断（避免设计规范 §8.4 警告的「整单落在起始月」错法）。
 * <p>
 * 依赖方向恒为 {@code finance → 本端口 ← leave}，不产生环。
 */
public interface ApprovedLeaveDaysPort {

    /**
     * 某员工在 {@code [startDate, endDate]} 内「已批请假（APPROVED）」的计薪天数。
     * <p>
     * 口径与 Mock {@code leaveStore.approvedLeaveDays} 逐位一致：SCHEDULED 假别逐日查排班（排除轮休日），
     * NATURAL 假别按自然日；跨月单由调用方按账期区间切分。
     *
     * @param employeeId 员工 id
     * @param startDate  区间起（含；null 表示不限）
     * @param endDate    区间止（含；null 表示不限）
     * @return 已批请假计薪天数（半天粒度，0 表示无）
     */
    BigDecimal approvedLeaveDays(Long employeeId, LocalDate startDate, LocalDate endDate);

    /**
     * 请假扣款开关（{@code leave_setting} 单行；无行时按配置默认，Q6 未裁定前为 false）。
     * <p>
     * true = 请假按缺勤计（扣款，{@code absentCount} 不减除请假天数）；
     * false = 默认，请假不计缺勤（{@code absentCount} 减除已批请假天数）。
     */
    boolean leaveDeductEnabled();
}
