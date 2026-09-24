package com.qiujie.dto.finance;

import lombok.Data;

import java.util.List;

/**
 * 工资单批量生成入参（对齐 Mock {@code generate}）。
 * <p>
 * 员工筛选三选一/组合：{@code employeeIds} 显式指定，{@code stationId} 按驿站，{@code deptId} 按部门；
 * 均未传则对全部在职员工生成。
 */
@Data
public class PayrollGenerateRequest {

    /** 账期 yyyy-MM（必填） */
    private String month;

    /** 驿站筛选（可空） */
    private Long stationId;

    /** 部门筛选（可空） */
    private Long deptId;

    /** 员工筛选（可空；非空数组时仅这些员工） */
    private List<Long> employeeIds;

    /** 计薪规则（可空；未传取第一个启用规则） */
    private Long ruleId;
}
