package com.qiujie.vo;

import lombok.Data;

/**
 * 登录响应中的员工简要信息（api.md 4.1.1）。
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
}
