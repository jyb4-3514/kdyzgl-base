package com.qiujie.service.finance;

import com.qiujie.dto.finance.MyPayrollQuery;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.dto.finance.PayrollItemAddRequest;
import com.qiujie.dto.finance.PayrollItemAdjustRequest;
import com.qiujie.dto.finance.PayrollManualAdjustmentQuery;
import com.qiujie.dto.finance.PayrollPublishRequest;
import com.qiujie.dto.finance.PayrollQuery;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementRef;
import com.qiujie.vo.finance.MyPayrollPageVO;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollLogVO;
import com.qiujie.vo.finance.PayrollManualAdjustmentSummaryVO;
import com.qiujie.vo.finance.PayrollPageVO;
import com.qiujie.vo.finance.PayrollPublishVO;
import com.qiujie.vo.finance.PayrollSubmitVO;
import com.qiujie.vo.finance.PayrollVO;

import java.util.List;

/**
 * 工资单服务（13 接口 + P5 结算单创建）。
 * <p>
 * 八态状态机、幂等生成（9405，按驿站收敛）、员工可见范围（PUBLISHED/CONFIRMED/PAID）、越权口径（9404）、
 * 全链路留痕（payroll_log）均集中在本服务；金额由规则驱动（{@code PayrollResolverRegistry}），本服务不含任何计薪项专属公式。
 * <p>
 * 所有写入口进入业务分支前统一过 {@code PayrollStateMachine.assertMutable}（PAID → 9413）。
 */
public interface PayrollService {

    /** 我的工资单（任意角色；仅本人且仅已发布/已确认/已发放） */
    MyPayrollPageVO myPayrolls(MyPayrollQuery query);

    /** 按月批量生成草稿（仅 ADMIN；同月同驿站已提交/已发布 → 9405；草稿/驳回覆盖重建并保留 MANUAL 明细） */
    PayrollGenerateVO generate(PayrollGenerateRequest request);

    /** 批量提交审核（仅 ADMIN；ids 非空数组；草稿/驳回/异议退回 → 待审核；写留痕） */
    PayrollSubmitVO submit(List<Long> ids);

    /** 批量发布/再发布（仅 ADMIN；ids 或 month+stationId 二选一；APPROVED 或 OBJECTED 可发布） */
    PayrollPublishVO publish(PayrollPublishRequest request);

    /** 工资单列表（仅 ADMIN；附各状态计数） */
    PayrollPageVO list(PayrollQuery query);

    /** 工资单详情（ADMIN 全量；其余角色仅本人且仅已发布/已确认/已发放：越权 9404、未发布 9403） */
    PayrollVO detail(Long id);

    /** 修改人工项金额（仅 ADMIN；isItemEditable 且仅 MANUAL 项；事由必填 2-200；改动后重算合计并留痕） */
    PayrollVO updateItems(Long id, String reason, List<PayrollItemAdjustRequest> items);

    /** 手工加/扣款（仅 ADMIN，I-6；isItemEditable；事由必填；服务端生成 item_key；重算合计并留痕） */
    PayrollVO addItem(Long id, PayrollItemAddRequest request);

    /** 审核（仅 ADMIN；待审核 → 已通过/已驳回；写留痕） */
    PayrollVO approve(Long id, Boolean approved, String approveRemark);

    /** 员工确认（仅本人 + 已发布 → 已确认；写留痕） */
    PayrollVO confirm(Long id);

    /** 员工提异议（仅本人 + 已发布 → 异议退回并记原因；写留痕） */
    PayrollVO objection(Long id, String reason);

    /** 确认发放归档（仅 ADMIN，I-8；已确认 → 已发放；PAID 后一律 9413；写留痕） */
    PayrollVO pay(Long id, String remark);

    /** 工资单操作留痕（I-7；ADMIN 全量字段，非 ADMIN 仅本人可见单且服务端裁剪敏感快照） */
    List<PayrollLogVO> logs(Long id);

    /**
     * 手工加/扣款对账汇总（I-10，仅 ADMIN）：按 {@code payroll_log.employee_id + month} 冗余定位列，
     * 汇总各员工加/扣款笔数、总额与净影响，并给合计行。回答「谁在何时把谁加/扣了多少、理由是什么」
     * （安全补偿控制；覆盖重建后仍可汇总）。
     * <p>计入动作：{@code ITEM_ADD}（新增加/扣款）与 {@code ITEM_UPDATE}（改已有 MANUAL 项金额）；
     * 后者净影响按 {@code after − before} 差值计（加款方向为正、扣款方向为负）。
     */
    PayrollManualAdjustmentSummaryVO manualAdjustmentSummary(PayrollManualAdjustmentQuery query);

    /**
     * 自动路径「提交审核未执行」留痕（B4a 修补）：自动算薪生成后逐单自动 {@code submit}，失败的单保持
     * {@code DRAFT}，由本方法以 {@code SYSTEM} 主体追加一条 {@code payroll_log(AUTO_SUBMIT_SKIPPED)}，
     * 使「为何该单仍是草稿」在审计时间线中可判定。
     * <p>为什么由本服务收口：{@code payroll_log} 的写入口径（操作人快照 / 白名单快照 / 主体类型）集中在此，
     * 若编排层直连 Mapper 另行拼装必然漂移。本方法自身吞异常（仅告警），不阻断调度主流程。
     */
    void recordAutoSubmitSkipped(Long payrollId, String reason);

    /** 创建离职结算单（由 P5 人事域经跨域端口调用；同员工同月只生成一次，幂等） */
    PayrollSettlementRef createSettlement(PayrollSettlementCommand command);
}
