package com.qiujie.service.finance;

/**
 * 锁定工资单引用（供 P7 请假撤回判定账期锁时回填提示）。
 *
 * @param payrollId  工资单 id
 * @param payrollNo  工资单号
 * @param status     工资单状态
 */
public record PayrollLockInfo(Long payrollId, String payrollNo, String status) {
}
