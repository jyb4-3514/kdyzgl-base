package com.qiujie.vo.leave;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 请假试算出参（api.md §7.1 #2 {@code {naturalDays, countedDays, hasRestDayExcluded}}）。
 * <p>
 * 计薪天数与算薪单点同源（S5），页面不得再实现一份镜像。
 */
@Data
public class LeavePreviewVO {

    /** 自然天数 */
    private BigDecimal naturalDays;

    /** 计薪天数（SCHEDULED 假别逐日查排班，排除轮休日） */
    private BigDecimal countedDays;

    /** 是否排除了轮休日（计薪 &lt; 自然） */
    private boolean hasRestDayExcluded;
}
