package com.qiujie.vo.kpi;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分规则出参（对齐 Mock {@code scoreRule{mode,fullScore}}）。
 * 单独成类是为了在「指标配置」与「得分明细逐项」两处复用同一嵌套结构。
 */
@Data
public class KpiScoreRuleVO {

    /** LINEAR / TIERED / BINARY */
    private String mode;

    /** 单项满分 */
    private BigDecimal fullScore;
}
