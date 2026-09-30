package com.qiujie.vo.employee;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工 VO（api.md 4.3.1 / 4.3.2，手机号脱敏）。
 * <p>
 * C-06：字段集对齐前端契约（Mock {@code db.js#toEmployeeVO}），补充 {@code pwdChanged}（只增不减）。
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
    /** 岗位（取值 店员 / 站长 / 管理员；未登记为 null） */
    private String position;
    private Integer status;

    /** 是否已修改过初始密码（C-06 补字段，对齐 Mock toEmployeeVO） */
    private Boolean pwdChanged;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
