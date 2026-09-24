package com.qiujie.vo.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计薪规则出参（对齐 Mock {@code toRuleVO}）。
 */
@Data
public class PayrollRuleVO {

    private Long id;

    private String ruleName;

    private String remark;

    /** 0=停用，1=启用 */
    private Integer status;

    private String statusLabel;

    /** 规则项总数 */
    private Integer itemCount;

    /** 启用项数 */
    private Integer enabledItemCount;

    private List<PayrollRuleItemVO> items;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
