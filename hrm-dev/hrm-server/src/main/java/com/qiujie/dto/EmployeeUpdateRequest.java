package com.qiujie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑员工请求（api.md 4.3.4）：
 * 与新增一致但无 username（不可修改）、无 password（改密走专用接口）；
 * 其余可选字段以请求体为准（null 即清空归属）。
 */
@Data
public class EmployeeUpdateRequest {

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

    private Long deptId;

    private Long stationId;

    /** 角色可修改，受自我保护（2001）与最后管理员保护（2002）约束 */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(ADMIN|STAFF)$", message = "角色取值仅支持 ADMIN/STAFF")
    private String role;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "入职日期格式须为 yyyy-MM-dd")
    private String entryDate;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
