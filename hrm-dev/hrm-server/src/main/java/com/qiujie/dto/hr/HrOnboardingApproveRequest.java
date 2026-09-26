package com.qiujie.dto.hr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * R-6 入职审批通过（聚合联动）入参 —— 「表 B：ADMIN 赋值字段」（registration-design §3.2 R-6 / §11.5）。
 * <p>
 * <b>与注册 DTO 严格分离</b>：本 DTO 的字段仅 ADMIN 可提交，注册方无法影响；
 * 其中 {@code role/deptId/position/薪资/initialPassword} 是「防注册注入」的核心（§5.4）。
 * <p>
 * 口径（定稿）：{@code deptId} 必填（U-09）、{@code initialPassword} 必填（U-02/M-4，<b>不复用注册密码</b>）、
 * 薪资三项必填（U-10）、{@code position} 必填（主智能体裁定：审批台岗位必填，缺失即拒）；
 * {@code role} 缺省 {@code STAFF}；{@code username} 缺省按 {@code u}+手机号生成（U-11）。
 */
@Data
public class HrOnboardingApproveRequest {

    /** 登录账号（缺省按 {@code u}+手机号规则生成；活跃唯一 1003） */
    @Size(max = 30, message = "登录账号长度不合法")
    private String username;

    /** ADMIN 一次性初始口令（必填；8-20 位含字母数字；员工首登强制改密，不复用注册密码） */
    @NotBlank(message = "请设置初始密码")
    private String initialPassword;

    /** 部门（必填；不存在 → 3001） */
    @NotNull(message = "请选择部门")
    private Long deptId;

    /** 驿站（缺省取流程意向驿站；停用 → 4004） */
    private Long stationId;

    /** 角色（缺省 STAFF；仅 STATION_ADMIN / STAFF） */
    private String role;

    /** 岗位（必填；同时双写 employee.position（权威）与 hr_flow.position（留痕）） */
    @NotBlank(message = "请填写岗位")
    @Size(max = 50, message = "岗位不超过 50 字")
    private String position;

    /** 试用期（月；缺省取 hrm.hr.default-probation-months） */
    @PositiveOrZero(message = "试用期月数不合法")
    private Integer probationMonths;

    /** 合同类型（缺省 FIXED_TERM） */
    private String contractType;

    /** 基本工资（必填） */
    @NotNull(message = "请填写基本工资")
    @PositiveOrZero(message = "基本工资不合法")
    private BigDecimal basicSalary;

    /** 岗位工资（必填） */
    @NotNull(message = "请填写岗位工资")
    @PositiveOrZero(message = "岗位工资不合法")
    private BigDecimal postSalary;

    /** 绩效基数（必填） */
    @NotNull(message = "请填写绩效基数")
    @PositiveOrZero(message = "绩效基数不合法")
    private BigDecimal performanceBase;

    /** 津贴项（可选） */
    private List<SalaryAllowanceItem> allowances;

    /** 定薪生效日期（缺省取流程预计入职日期） */
    private String effectiveDate;

    /** 办理备注（0-200 字） */
    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
