package com.qiujie.dto.finance;

import lombok.Data;

import java.util.Map;

/**
 * 计薪规则项入参（对齐 Mock {@code validateItems}）。
 * <p>
 * {@code params} 结构随来源而变（FIXED/ATTENDANCE/KPI/MANUAL），故以 {@code Map} 承载；
 * 各来源的具体参数由算薪注册表按 source 解释（新增来源不改本 DTO）。
 */
@Data
public class PayrollRuleItemRequest {

    /** 规则项键（1-30，须匹配 {@code ^[A-Z][A-Z0-9_]*$}） */
    private String key;

    /** 规则项名（1-20 字） */
    private String name;

    /** 类型：ADDITION / DEDUCTION */
    private String type;

    /** 来源：FIXED / ATTENDANCE / KPI / MANUAL */
    private String source;

    /** 来源参数（对象；结构随来源） */
    private Map<String, Object> params;

    /** 启用：0/1（缺省 1） */
    private Integer enabled;

    /** 排序（缺省按数组下标 +1） */
    private Integer sortOrder;
}
