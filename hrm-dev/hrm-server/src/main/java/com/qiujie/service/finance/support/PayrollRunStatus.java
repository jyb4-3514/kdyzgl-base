package com.qiujie.service.finance.support;

/**
 * 自动算薪运行结果（db.md §8.6.7 / DDL: V20 {@code payroll_run.status}）。
 * <p>
 * 与 claim 槽位的关系（方案 §3.3）：{@code RUNNING}/{@code SUCCESS}/{@code SKIPPED} 写 {@code claim_key}（占位）；
 * {@code FAILED} 置 {@code claim_key=NULL}（释放，允许次日重试）。
 */
public enum PayrollRunStatus {

    /** 已认领、执行中（占位） */
    RUNNING,
    /** 成功（占位，跨日永久不再尝试） */
    SUCCESS,
    /** 失败（可重试类，次日继续；释放跨日占位） */
    FAILED,
    /** 业务性跳过（终止类，不重试；占位） */
    SKIPPED;

    public static boolean isValid(String code) {
        for (PayrollRunStatus status : values()) {
            if (status.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
