package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.entity.PayrollRun;
import com.qiujie.mapper.PayrollRunMapper;
import com.qiujie.service.finance.PayrollService;
import com.qiujie.service.finance.support.PayrollRunFailureSupport;
import com.qiujie.service.finance.support.PayrollRunStatus;
import com.qiujie.vo.finance.PayrollGenerateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动算薪事务分段处理器（Tx1 / Tx2 / Tx3 + 僵死回收）。
 * <p>
 * <b>为什么独立成 Bean</b>：Spring 同类内部方法调用的 {@code @Transactional} 不生效（不走代理）；
 * 把三段交易独立到本类，由编排层 {@code PayrollRunServiceImpl}（无事务）跨 Bean 调用，
 * 保证「claim 短事务先提交 → 生成业务事务 → 终态回写」的真实三段边界（方案 §3.3 事务切分）。
 * <p>
 * 全部使用 {@code REQUIRES_NEW}：确保与编排层上下文无事务耦合，每段自成一个提交单元。
 */
@Service
@RequiredArgsConstructor
public class PayrollRunTxHandler {

    private final PayrollRunMapper payrollRunMapper;
    private final PayrollService payrollService;

    /** Tx1：认领槽位（INSERT RUNNING）；撞 {@code uk_attempt}/{@code uk_claim} 时 DuplicateKeyException 穿透以回滚本段 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PayrollRun claim(PayrollRun run) {
        payrollRunMapper.insert(run);
        return run;
    }

    /** Tx2：业务事务；内部复用既有 {@code generate}（同一事务），失败整体回滚 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PayrollGenerateVO executeGenerate(Long stationId, String month) {
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setMonth(month);
        request.setStationId(stationId);
        return payrollService.generate(request);
    }

    /**
     * Tx2b：自动提交单张（Q6 生成即自动提交）。<b>逐单独立事务</b>（{@code REQUIRES_NEW}）——
     * 复用既有 {@link PayrollService#submit(List)} 落 {@code PENDING_APPROVAL}；某单失败只回滚本单，
     * 由编排层逐单 try/catch 隔离，<b>不回滚已成功提交的其它单，也不回滚已生成的单据</b>。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void submitOne(Long payrollId) {
        payrollService.submit(List.of(payrollId));
    }

    /** 自动提交失败留痕（独立事务）：以 {@code SYSTEM} 主体追加 {@code payroll_log(AUTO_SUBMIT_SKIPPED)}，不抛出 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordSubmitSkipped(Long payrollId, String reason) {
        payrollService.recordAutoSubmitSkipped(payrollId, reason);
    }

    /**
     * Tx3：终态回写（成功 / 跳过 / 失败）。
     * <p>{@code FAILED} 时 {@code claimKey} 传 {@code null} → 释放跨日占位（允许次日重试）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(Long runId, String status, String claimKey, String skipCode, String skipReason,
                       Integer generatedCount, String failReason, LocalDateTime finishTime) {
        payrollRunMapper.update(null, new LambdaUpdateWrapper<PayrollRun>()
                .eq(PayrollRun::getId, runId)
                .set(PayrollRun::getStatus, status)
                .set(PayrollRun::getClaimKey, claimKey)
                .set(PayrollRun::getSkipCode, skipCode)
                .set(PayrollRun::getSkipReason, skipReason)
                .set(PayrollRun::getGeneratedCount, generatedCount)
                .set(PayrollRun::getFailReason, failReason)
                .set(PayrollRun::getFinishTime, finishTime)
                .set(PayrollRun::getUpdateTime, finishTime));
    }

    /**
     * 僵死回收（算法 §1.5.3）：扫描超期 {@code RUNNING}，逐行 CAS 更新为 {@code FAILED} 并释放占位。
     * <p>CAS 条件含 {@code status=RUNNING}：已终态行不匹配（{@code affected=0}）→ 不重复回收、不重复告警。
     *
     * @return 实际回收命中的行（供编排层触发失败告警钩子）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<PayrollRun> reclaimStale(LocalDateTime threshold, LocalDateTime now) {
        List<PayrollRun> candidates = payrollRunMapper.selectList(new LambdaQueryWrapper<PayrollRun>()
                .eq(PayrollRun::getStatus, PayrollRunStatus.RUNNING.name())
                .lt(PayrollRun::getStartTime, threshold));
        List<PayrollRun> reclaimed = new ArrayList<>();
        for (PayrollRun row : candidates) {
            int affected = payrollRunMapper.update(null, new LambdaUpdateWrapper<PayrollRun>()
                    .eq(PayrollRun::getId, row.getId())
                    .eq(PayrollRun::getStatus, PayrollRunStatus.RUNNING.name())
                    .set(PayrollRun::getStatus, PayrollRunStatus.FAILED.name())
                    .set(PayrollRun::getClaimKey, null)
                    .set(PayrollRun::getFailReason, PayrollRunFailureSupport.STALE_RECLAIMED)
                    .set(PayrollRun::getFinishTime, now)
                    .set(PayrollRun::getUpdateTime, now));
            if (affected > 0) {
                row.setStatus(PayrollRunStatus.FAILED.name());
                row.setClaimKey(null);
                row.setFailReason(PayrollRunFailureSupport.STALE_RECLAIMED);
                row.setFinishTime(now);
                reclaimed.add(row);
            }
        }
        return reclaimed;
    }
}
