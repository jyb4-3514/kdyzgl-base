package com.qiujie.entity;

import lombok.Data;

/**
 * 打卡时段（{@code attendance_rule.check_periods} JSON 内嵌结构，非独立表）。
 * <p>
 * 时段是打卡时间判定的唯一真源：{@code work_start_time / work_end_time} 是「首段开始 / 末段结束」的派生值
 * （对齐 Mock {@code ruleSeed} 与 {@code saveRule} 的重算口径）。
 * 字段名与 JSON 键逐字一致（{@code name/startTime/endTime}），由 Jackson 直接映射，不做驼峰重命名。
 */
@Data
public class CheckPeriod {

    /** 时段名（1-20 字） */
    private String name;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm，可 24:00 表示跨零点收班 */
    private String endTime;
}
