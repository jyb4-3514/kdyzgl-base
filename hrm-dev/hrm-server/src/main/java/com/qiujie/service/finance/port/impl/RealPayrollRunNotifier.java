package com.qiujie.service.finance.port.impl;

import com.qiujie.entity.PayrollRun;
import com.qiujie.service.finance.port.PayrollRunNotifier;
import com.qiujie.service.finance.support.PayrollNotifySupport;
import com.qiujie.service.finance.support.PayrollRunFailureSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自动算薪运行事件通知端口真实实现（B4a）——取代 {@link NoopPayrollRunNotifier}（{@code @ConditionalOnMissingBean} 自动退让）。
 * <p>
 * 调用点位于驿站事务<b>提交之后</b>（{@code PayrollRunServiceImpl} 编排层无事务），故此处再包一层 try/catch：
 * 即便 {@link PayrollNotifySupport} 内部已吞异常，仍防御未来改动引入的漏网异常，<b>绝不外溢阻断调度主流程</b>。
 * <p>
 * {@code @Primary}：与 {@link NoopPayrollRunNotifier}（{@code @ConditionalOnMissingBean} 兜底）同存时，
 * 注入一律解析到本真实实现，避免 bean 装配顺序导致的 NoUniqueBean 启动失败。
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class RealPayrollRunNotifier implements PayrollRunNotifier {

    private final PayrollNotifySupport notifySupport;

    @Override
    public void onSucceeded(PayrollRun run, List<Long> pendingApprovalPayrollIds, int pendingApprovalCount) {
        try {
            notifySupport.notifyPendingApproval(run, pendingApprovalPayrollIds, pendingApprovalCount);
        } catch (Exception e) {
            log.warn("NOTIFY_SKIP 自动算薪成功通知异常：runId={}", run == null ? null : run.getId(), e);
        }
    }

    @Override
    public void onFailed(PayrollRun run) {
        try {
            notifySupport.notifyRunFailed(run, run == null ? null : run.getFailReason());
        } catch (Exception e) {
            log.warn("NOTIFY_SKIP 自动算薪失败告警异常：runId={}", run == null ? null : run.getId(), e);
        }
    }

    @Override
    public void onStaleReclaimed(List<PayrollRun> reclaimed) {
        try {
            if (reclaimed == null) {
                return;
            }
            for (PayrollRun run : reclaimed) {
                // 僵死回收命中即告警（消除静默失效，算法 §1.5.3）；reason 固定为机器可辨识标记
                notifySupport.notifyRunFailed(run, PayrollRunFailureSupport.STALE_RECLAIMED);
            }
        } catch (Exception e) {
            log.warn("NOTIFY_SKIP 僵死回收告警异常：命中 {} 条", reclaimed == null ? 0 : reclaimed.size(), e);
        }
    }
}
