package com.qiujie.vo.finance;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工加/扣款对账——按员工汇总行（I-10，api.md §4.12.22）。
 * <p>
 * 加款 = {@code itemType=ADDITION} 的手工项；扣款 = {@code itemType=DEDUCTION}；
 * {@code netImpact = additionTotal − deductionTotal}（对实发的净影响方向：净增为正）。
 * 合计行复用本类，{@code employeeId=null}、{@code employeeName='合计'}。
 * <p>
 * <b>覆盖动作（v1.4/B4a 修补）</b>：{@code ITEM_ADD}（新增加/扣款）+ {@code ITEM_UPDATE}（改既有 MANUAL 项金额）。
 * 既有的 {@code additionCount/additionTotal/deductionCount/deductionTotal/netImpact} 保持 <b>ITEM_ADD 口径不变</b>（向后可读）；
 * 改金额的净影响另以 {@code updateIncreaseTotal / updateDecreaseTotal / totalNetImpact} 表达。
 */
@Data
public class PayrollManualAdjustmentEmployeeVO {

    private Long employeeId;

    private String employeeName;

    /** 加款笔数（ITEM_ADD 的 ADDITION 明细计数） */
    private int additionCount;

    /** 加款总额（ITEM_ADD） */
    private BigDecimal additionTotal = BigDecimal.ZERO;

    /** 扣款笔数（ITEM_ADD 的 DEDUCTION 明细计数） */
    private int deductionCount;

    /** 扣款总额（ITEM_ADD） */
    private BigDecimal deductionTotal = BigDecimal.ZERO;

    /** 净影响 = 加款总额 − 扣款总额（ITEM_ADD 口径，语义不变） */
    private BigDecimal netImpact = BigDecimal.ZERO;

    /** 新增动作笔数（ITEM_ADD 留痕条数） */
    private int addCount;

    /** 改金额动作笔数（ITEM_UPDATE 留痕条数） */
    private int updateCount;

    /** 改金额对实发的净增合计（≥0）：加款项增额、扣款项减额均计入此列 */
    private BigDecimal updateIncreaseTotal = BigDecimal.ZERO;

    /** 改金额对实发的净减合计（≥0，正数表示减少额）：加款项减额、扣款项增额均计入此列 */
    private BigDecimal updateDecreaseTotal = BigDecimal.ZERO;

    /** 总净影响 = netImpact + updateIncreaseTotal − updateDecreaseTotal（含新增加/扣款与改金额） */
    private BigDecimal totalNetImpact = BigDecimal.ZERO;
}
