package com.qiujie.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改本人密码请求（api.md 4.1.4）。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$", message = "新密码须为8-20位，且同时包含字母和数字")
    private String newPassword;
}
