package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 保存驿站算薪配置入参（I-3，api.md §4.12.16）。
 * <p>
 * 各字段允许为空 = 「不修改 / 新建时取 DDL 默认」；非空时由 Service 校验并给出可判定错误码：
 * {@code payrollDay} 越界 → {@code 9407}、{@code payrollTime} 非法 → {@code 9408}。
 * <b>不</b>用 {@code @Min/@Max} 注解承载范围校验——那会先被 {@code @Valid} 拦成通用 400，覆盖掉 9407/9408 语义。
 */
@Data
public class StationPayrollSettingSaveRequest {

    /** 是否启用自动算薪：0=停用，1=启用（可空 = 不变） */
    private Integer enabled;

    /** 算薪日（1-31，可空 = 不变；越界 → 9407） */
    private Integer payrollDay;

    /** 执行时间 HH:mm（可空 = 不变；非法 → 9408） */
    private String payrollTime;

    /** 生成后是否推送管理员：0/1（可空 = 不变） */
    private Integer notifyEnabled;

    /** 备注（可空；≤255） */
    private String remark;
}
