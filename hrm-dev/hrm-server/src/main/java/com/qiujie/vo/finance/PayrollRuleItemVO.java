package com.qiujie.vo.finance;

import lombok.Data;

import java.util.Map;

/**
 * 计薪规则项出参（对齐 Mock {@code ruleItemVO}）。
 */
@Data
public class PayrollRuleItemVO {

    /** 规则项 id（编辑时整体覆盖会重建，前端不依赖其稳定） */
    private Long id;

    private String key;

    private String name;

    private String type;

    private String typeLabel;

    private String source;

    private String sourceLabel;

    /** 来源参数（结构随来源） */
    private Map<String, Object> params;

    /** 1=启用，0=停用 */
    private Integer enabled;

    private Integer sortOrder;
}
