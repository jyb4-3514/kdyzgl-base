package com.qiujie.dto.employee;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增员工请求（api.md 4.3.3）。
 */
@Data
public class EmployeeCreateRequest {

    @NotBlank(message = "登录账号不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,29}$",
            message = "登录账号须以字母开头，4-30位，仅可含字母、数字、下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$", message = "密码须为8-20位，且同时包含字母和数字")
    private String password;

    @NotBlank(message = "员工姓名不能为空")
    @Size(min = 1, max = 50, message = "员工姓名须为1-50个字符")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确，须为11位有效手机号")
    private String phone;

    /** 性别：0=未知，1=男，2=女（默认 0） */
    @Min(value = 0, message = "性别取值仅支持 0/1/2")
    @Max(value = 2, message = "性别取值仅支持 0/1/2")
    private Integer gender;

    /** 所属部门（须为存在且未删除的部门） */
    private Long deptId;

    /** 归属驿站（须为存在、未删除且启用的驿站） */
    private Long stationId;

    /**
     * 角色白名单（ARCH-C-1 放开 {@code STATION_ADMIN}）。
     * <p>
     * {@code STATION_ADMIN} 须同时满足「归属启用驿站」，由 Service 层做条件必填校验（DTO 只约束取值集合）。
     */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(ADMIN|STATION_ADMIN|STAFF)$", message = "角色取值仅支持 ADMIN/STATION_ADMIN/STAFF")
    private String role;

    /** 入职日期 yyyy-MM-dd */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "入职日期格式须为 yyyy-MM-dd")
    private String entryDate;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
