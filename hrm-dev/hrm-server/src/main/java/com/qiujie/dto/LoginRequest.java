package com.qiujie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求（api.md 4.1.1）。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 30, message = "用户名须为4-30位字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
