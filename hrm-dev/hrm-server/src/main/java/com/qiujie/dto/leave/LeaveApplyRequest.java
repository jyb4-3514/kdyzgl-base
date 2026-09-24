package com.qiujie.dto.leave;

import lombok.Data;

/**
 * 请假表单入参（提交 / 编辑 / 重提共用，api.md §7.1 #1/#8/#10）。
 * <p>
 * 字段用 String 承载日期与枚举：格式校验返回精确的 400 文案（「xxx 格式须为 YYYY-MM-DD」），
 * 语义校验（真实日期 / 方向 / 上限）返回 9604，故不交由 {@code @DateTimeFormat} 提前抛出笼统错误。
 */
@Data
public class LeaveApplyRequest {

    /** 假别：ANNUAL/PERSONAL/SICK/COMPENSATORY/MARRIAGE/MATERNITY/PATERNITY/BEREAVEMENT/OTHER */
    private String leaveType;

    /** 开始日期 yyyy-MM-dd */
    private String startDate;

    /** 开始半天粒度：AM / PM */
    private String startPeriod;

    /** 结束日期 yyyy-MM-dd */
    private String endDate;

    /** 结束半天粒度：AM / PM */
    private String endPeriod;

    /** 请假事由（2-200 字） */
    private String reason;
}
