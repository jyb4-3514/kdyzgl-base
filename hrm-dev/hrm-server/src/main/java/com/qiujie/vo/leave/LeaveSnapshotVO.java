package com.qiujie.vo.leave;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计薪天数快照（{@code counted_days_snapshot} JSON 的对象形态，api.md §7.4）。
 * <p>
 * 终审通过时落库（自然天数 / 计薪天数 / 排班摘要），使排班事后变更不影响已出账口径。
 */
@Data
public class LeaveSnapshotVO {

    /** 自然天数 */
    private BigDecimal naturalDays;

    /** 计薪天数 */
    private BigDecimal countedDays;

    /** 排班摘要（区间内有排班的日期排序拼接串；NATURAL 假别为空串） */
    private String scheduleDigest;
}
