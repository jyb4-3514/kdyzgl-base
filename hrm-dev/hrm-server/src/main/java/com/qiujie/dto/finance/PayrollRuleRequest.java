package com.qiujie.dto.finance;

import lombok.Data;

import java.util.List;

/**
 * 计薪规则新建 / 编辑入参（对齐 Mock {@code routes/finance.js} createRule/updateRule）。
 * <p>
 * 字段语义：{@code items} 传入即为整体覆盖（编辑时全量替换规则项）；校验见 {@code PayrollRuleValidator}。
 */
@Data
public class PayrollRuleRequest {

    /** 规则名（2-50 字；新建必填，编辑可空表示不改） */
    private String ruleName;

    /** 备注（≤200 字；null 表示清空） */
    private String remark;

    /** 状态：0=停用，1=启用（可空表示不改） */
    private Integer status;

    /** 规则项（新建必填非空；编辑传入即整体覆盖） */
    private List<PayrollRuleItemRequest> items;
}
