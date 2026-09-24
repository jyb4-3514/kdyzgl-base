package com.qiujie.vo.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 登录响应中的员工信息（api.md 4.1.1）。
 * <p>
 * C-06：字段集对齐前端契约（Mock {@code db.js#toEmployeeVO}），**只增不减**——
 * 前端登录后读 {@code data.employee.stationId}（架构 3.3 F-1），缺失会导致站长/员工端拿不到归属驿站。
 */
@Data
public class LoginEmployeeVO {

    private Long id;
    private String username;
    private String realName;

    /** 手机号脱敏（决策 D5） */
    private String phone;

    private String role;

    /** 是否已修改过初始密码（false 时前端强制进入改密流程） */
    private Boolean pwdChanged;

    // ==================== C-06 新增（与 EmployeeVO / toEmployeeVO 同构） ====================

    private Integer gender;

    private Long deptId;
    private String deptName;
    private Long stationId;
    private String stationName;

    /** 状态：0=禁用，1=启用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
