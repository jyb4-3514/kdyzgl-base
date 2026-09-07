package com.qiujie.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工 VO（api.md 4.3.1 / 4.3.2，手机号脱敏）。
 */
@Data
public class EmployeeVO {

    private Long id;
    private String username;
    private String realName;

    /** 手机号脱敏（决策 D5） */
    private String phone;
    private Integer gender;

    private Long deptId;
    private String deptName;
    private Long stationId;
    private String stationName;
    private String role;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
