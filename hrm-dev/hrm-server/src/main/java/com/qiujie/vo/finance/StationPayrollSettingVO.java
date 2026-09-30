package com.qiujie.vo.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 驿站算薪配置出参（I-1 / I-2，api.md §4.12.16）。
 * <p>未配置驿站由服务端以 DDL 默认值填充（enabled=0 / payrollDay=1 / payrollTime=09:00 / notifyEnabled=1），
 * 使「驿站列表」形态稳定；而 I-2 对未配置驿站返回 {@code 9406}（可判定分支）。
 */
@Data
public class StationPayrollSettingVO {

    private Long stationId;

    private String stationName;

    /** 是否启用自动算薪：0/1 */
    private Integer enabled;

    /** 算薪日（1-31，月末钳位） */
    private Integer payrollDay;

    /** 执行时间 HH:mm */
    private String payrollTime;

    /** 生成后是否推送管理员：0/1 */
    private Integer notifyEnabled;

    private String remark;

    /** 配置最近更新时间（未配置为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
