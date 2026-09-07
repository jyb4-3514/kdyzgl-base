package com.qiujie.vo;

import lombok.Data;

/**
 * 登录响应（api.md 4.1.1）。
 */
@Data
public class LoginVO {

    private String token;

    /** Token 有效期（秒） */
    private Long expiresIn;

    private LoginEmployeeVO employee;
}
