package com.qiujie.vo.station;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 驿站 VO（api.md 4.5.1，联系电话脱敏）。
 */
@Data
public class StationVO {

    private Long id;
    private String code;
    private String stationName;
    private String contactPerson;

    /** 联系电话脱敏（规则同手机号，决策 D5） */
    private String contactPhone;
    private String address;
    private Integer status;
    private Long employeeCount;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
