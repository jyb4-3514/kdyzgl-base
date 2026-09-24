package com.qiujie.service.hr.port.impl;

import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementPort;
import com.qiujie.service.hr.port.PayrollSettlementRef;

/**
 * 结算单端口降级实现（P6 财务域未实现期间的兜底）。
 * <p>
 * 行为口径与 Mock 一致：结算单创建失败 → 离职 SETTLEMENT 步骤不通过 → 不可离岗（9306）。
 * 这里直接抛 {@link ErrorCode#HR_SETTLEMENT_UNFINISHED}，避免造出虚假结算单号污染数据；
 * 待 P6 提供真实实现（{@code PayrollSettlementPort} Bean）后，本兜底 Bean 由
 * {@code HrPortConfig} 的 {@code @ConditionalOnMissingBean} 自动失效。
 */
public class UnavailablePayrollSettlementPort implements PayrollSettlementPort {

    @Override
    public PayrollSettlementRef createSettlement(PayrollSettlementCommand command) {
        // TODO(扩展): P6 落地后删除本降级实现（由财务域真实 PayrollSettlementPort Bean 顶替）
        throw new BusinessException(ErrorCode.HR_SETTLEMENT_UNFINISHED,
                "薪资结算单创建失败：财务域（P6）尚未就绪");
    }
}
