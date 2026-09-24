package com.qiujie.service.finance;

import java.util.Optional;

/**
 * 账期锁查询服务（财务域对 P7 请假域暴露的<b>只读端口</b>，架构 §2.2「PayrollLockQuery」）。
 * <p>
 * 用途：P7「撤回已批请假」需判定该员工该账期的工资单是否已出账——同月存在非 DRAFT/REJECTED 的工资单
 * 即视为锁定，撤回会让工资单与申请单对不上，故拒绝（P7 抛 9606）。
 * <p>
 * <b>签名（供 P7 注入调用）</b>：
 * <pre>
 * boolean isMonthLocked(Long employeeId, String month);
 * Optional&lt;PayrollLockInfo&gt; findLockingPayroll(Long employeeId, String month);
 * </pre>
 * 依赖方向恒为 {@code leave → finance}（下游读上游），不产生环。
 */
public interface PayrollLockQueryService {

    /**
     * 该员工该账期是否被工资单锁定。
     *
     * @param employeeId 员工 id
     * @param month      账期 yyyy-MM
     * @return 存在非 DRAFT/REJECTED 工资单 → true
     */
    boolean isMonthLocked(Long employeeId, String month);

    /**
     * 取锁定该账期的工资单（用于 9606 提示/日志）。
     *
     * @return 命中的工资单；无锁定则 {@link Optional#empty()}
     */
    Optional<PayrollLockInfo> findLockingPayroll(Long employeeId, String month);
}
