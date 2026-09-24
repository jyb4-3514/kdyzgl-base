package com.qiujie.service.finance;

import com.qiujie.dto.finance.MyPayrollQuery;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.dto.finance.PayrollItemAdjustRequest;
import com.qiujie.dto.finance.PayrollPublishRequest;
import com.qiujie.dto.finance.PayrollQuery;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementRef;
import com.qiujie.vo.finance.MyPayrollPageVO;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollPageVO;
import com.qiujie.vo.finance.PayrollPublishVO;
import com.qiujie.vo.finance.PayrollSubmitVO;
import com.qiujie.vo.finance.PayrollVO;

import java.util.List;

/**
 * 工资单服务（10 接口 + P5 结算单创建）。
 * <p>
 * 六态状态机、幂等生成（9405）、员工可见范围（仅 PUBLISHED/CONFIRMED）、越权口径（9404）均集中在本服务；
 * 金额由规则驱动（{@code PayrollResolverRegistry}），本服务不含任何计薪项专属公式。
 */
public interface PayrollService {

    /** 我的工资单（任意角色；仅本人且仅已发布/已确认） */
    MyPayrollPageVO myPayrolls(MyPayrollQuery query);

    /** 按月批量生成草稿（仅 ADMIN；同月已提交/已发布 → 9405；草稿/驳回覆盖重建） */
    PayrollGenerateVO generate(PayrollGenerateRequest request);

    /** 批量提交审核（仅 ADMIN；ids 非空数组；草稿/驳回 → 待审核） */
    PayrollSubmitVO submit(List<Long> ids);

    /** 批量发布（仅 ADMIN；ids 或 month+stationId 二选一；仅已通过被发布） */
    PayrollPublishVO publish(PayrollPublishRequest request);

    /** 工资单列表（仅 ADMIN；附各状态计数） */
    PayrollPageVO list(PayrollQuery query);

    /** 工资单详情（ADMIN 全量；其余角色仅本人且仅已发布/已确认：越权 9404、未发布 9403） */
    PayrollVO detail(Long id);

    /** 修改人工项金额（仅 ADMIN；仅 MANUAL 项且单据可编辑；改动后重算合计） */
    PayrollVO updateItems(Long id, List<PayrollItemAdjustRequest> items);

    /** 审核（仅 ADMIN；待审核 → 已通过/已驳回） */
    PayrollVO approve(Long id, Boolean approved, String approveRemark);

    /** 员工确认（仅本人 + 已发布 → 已确认） */
    PayrollVO confirm(Long id);

    /** 员工提异议（仅本人 + 已发布；退回待审核并记原因） */
    PayrollVO objection(Long id, String reason);

    /** 创建离职结算单（由 P5 人事域经跨域端口调用；同员工同月只生成一次，幂等） */
    PayrollSettlementRef createSettlement(PayrollSettlementCommand command);
}
