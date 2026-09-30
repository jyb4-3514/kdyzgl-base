package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;

/**
 * 手工加/扣款对账汇总出参（I-10，api.md §4.12.22；仅 ADMIN）。
 * <p>
 * 目的：回答「谁在何时把谁加/扣了多少、理由是什么」的安全补偿控制。汇总口径以
 * {@code payroll_log.employee_id + month} 冗余定位列为准（{@code generate} 覆盖重建会更换 {@code payroll_id}，
 * 按 {@code payroll_id} 关联会漏已删单的加扣款留痕）。
 */
@Data
public class PayrollManualAdjustmentSummaryVO {

    /** 账期（yyyy-MM） */
    private String month;

    /** 驿站过滤（回显；null = 全驿站） */
    private Long stationId;

    /** 按员工汇总行（employeeId 升序） */
    private List<PayrollManualAdjustmentEmployeeVO> list;

    /** 合计行（employeeId=null、employeeName='合计'） */
    private PayrollManualAdjustmentEmployeeVO total;
}
