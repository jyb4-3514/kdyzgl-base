package com.qiujie.dto.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员重置员工密码请求（api.md 4.3.7）。
 */
@Data
public class PasswordResetRequest {

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$", message = "新密码须为8-20位，且同时包含字母和数字")
    private String newPassword;
}
