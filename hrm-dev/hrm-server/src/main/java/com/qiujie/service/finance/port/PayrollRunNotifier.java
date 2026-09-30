package com.qiujie.service.finance.port;

import com.qiujie.entity.PayrollRun;

import java.util.List;

/**
 * 自动算薪运行事件通知端口（B3 仅留钩子；真实投递属 B4a）。
 * <p>
 * <b>为什么本批只留钩子</b>：通知投递（类型 7/8/9、收件人解析 {@code findAdminEmployeeIds()}、
 * {@code SYSTEM_TYPES} 白名单拆分）是 B4a 的落地范围；B3 只负责「数据落库 + 可观测钩子」，
 * 避免两批同时改通知链路产生冲突（任务边界：通知投递属 B4a）。
 * <p>
 * 实现约束（供 B4a）：调用点位于驿站事务<b>提交之后</b>，实现<b>必须 try/catch</b>，
 * 失败只写 {@code NOTIFY_SKIP} 留痕、不得外溢阻断调度主流程（沿用请假域口径）。
 */
public interface PayrollRunNotifier {

    /**
     * 自动算薪成功（{@code SUCCESS}，生成与逐单自动提交的事务均已提交）后投递类型 7。
     * <p>只对<b>确已落 {@code PENDING_APPROVAL}</b> 的单据投递——逐单自动提交失败者仍为 {@code DRAFT}，不得进「待审核」通知。
     *
     * @param run                       运行记录（已终态）
     * @param pendingApprovalPayrollIds 本次确已落 {@code PENDING_APPROVAL} 的工资单 id（可为空）
     * @param pendingApprovalCount      待审核单据数（= ids 大小）
     */
    void onSucceeded(PayrollRun run, List<Long> pendingApprovalPayrollIds, int pendingApprovalCount);

    /** 单驿站执行失败（{@code FAILED}；B4a 按 {@code alert-after-consecutive-fail-days} 做连续失败告警） */
    void onFailed(PayrollRun run);

    /** 僵死 RUNNING 被回收（{@code STALE_RECLAIMED}）；回收命中即触发，消除「静默失效」（算法 §1.5.3） */
    void onStaleReclaimed(List<PayrollRun> reclaimed);
}
