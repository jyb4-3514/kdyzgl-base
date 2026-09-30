package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 确认发放归档入参（api.md §4.12.13，I-8）：仅可选 {@code remark}，无必填项。
 * <p>为什么单独建 DTO 而非复用：{@code pay} 的入参语义（发放备注）与其它端点不同，
 * 复用会让契约漂移；独立小 DTO 成本极低。
 */
@Data
public class PayrollPayRequest {

    /** 发放备注（可空；写入留痕 remark） */
    private String remark;
}
