package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;

/**
 * 计薪规则列表出参（对齐 Mock {@code ruleList}：{@code { list }}，规则数少，不分页）。
 */
@Data
public class PayrollRuleListVO {

    private List<PayrollRuleVO> list;

    public PayrollRuleListVO() {
    }

    public PayrollRuleListVO(List<PayrollRuleVO> list) {
        this.list = list;
    }
}
