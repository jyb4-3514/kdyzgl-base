package com.qiujie.vo.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当前用户信息（api.md 4.1.3，手机号脱敏）。
 */
@Data
public class MeVO {

    private Long id;
    private String username;
    private String realName;

    /** 手机号脱敏（本人查看同样脱敏，决策 D5） */
    private String phone;
    private Integer gender;
    private String role;

    private Long deptId;
    private String deptName;
    private Long stationId;
    private String stationName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    private Boolean pwdChanged;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;
}
