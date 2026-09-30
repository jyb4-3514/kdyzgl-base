package com.qiujie.service.finance.port.impl;

import com.qiujie.entity.PayrollRun;
import com.qiujie.service.finance.port.PayrollRunNotifier;

import java.util.List;

/**
 * 自动算薪通知端口空实现（B3 占位；B4a 接入 {@code NotificationService} 后由真实 Bean 取代，见
 * {@link PayrollRunNotifierConfig} 的 {@code @ConditionalOnMissingBean}）。
 * <p>
 * TODO(扩展): B4a 实现类型 7「工资单待审核」投递（收件人 = 全部在职 ADMIN，单一真源
 *   {@code findAdminEmployeeIds()}；受该站 {@code notify_enabled} 与 {@code notify-on-fail} 管控），
 *   并替换本空实现；本类只保证「调度链路可运行、钩子已就位」。
 */
public class NoopPayrollRunNotifier implements PayrollRunNotifier {

    @Override
    public void onSucceeded(PayrollRun run, List<Long> pendingApprovalPayrollIds, int pendingApprovalCount) {
        // TODO(扩展): B4a 投递类型 7 通知（→管理员），事务提交后调用、失败不阻断
    }

    @Override
    public void onFailed(PayrollRun run) {
        // TODO(扩展): B4a 连续失败天数统计与告警（alert-after-consecutive-fail-days / alert-repeat-interval-days）
    }

    @Override
    public void onStaleReclaimed(List<PayrollRun> reclaimed) {
        // TODO(扩展): B4a 僵死回收告警（受 notify-on-fail 管控；回收命中必触发，见算法 §1.5.3）
    }
}
