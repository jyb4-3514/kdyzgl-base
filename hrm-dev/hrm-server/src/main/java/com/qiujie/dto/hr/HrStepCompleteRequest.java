package com.qiujie.dto.hr;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 流程步骤办理入参（入职与离职共用，对齐 Mock {@code completeOnboardingStep/completeOffboardingStep} 的 body）。
 * <p>
 * 字段按步骤语义取用：{@code CREATE_ACCOUNT} 用账号/密码/部门/驿站/试用期/合同类型，
 * {@code ASSIGN_STATION} 用部门/驿站/岗位/角色，{@code SET_SALARY} 用定薪字段；
 * 离职步骤仅用 {@code remark}。步骤级参数校验由 Service 按步骤语义执行（路由层只做通用字段把关）。
 */
@Data
public class HrStepCompleteRequest {

    /** 办理备注（0-200 字） */
    private String remark;

    /** 预计入职日期（yyyy-MM-dd，仅入职） */
    private String expectedEntryDate;

    // ---------- CREATE_ACCOUNT ----------

    /** 登录账号（字母开头，4-30 位字母数字下划线） */
    private String username;

    /** 初始密码（8-20 位且含字母与数字） */
    private String password;

    /** 部门（缺省取流程值） */
    private Long deptId;

    /** 驿站（缺省取流程值） */
    private Long stationId;

    /** 试用期（月） */
    private Integer probationMonths;

    /** 合同类型 */
    private String contractType;

    // ---------- ASSIGN_STATION ----------

    /** 岗位 */
    private String position;

    /** 角色（仅 STATION_ADMIN / STAFF） */
    private String role;

    // ---------- SET_SALARY ----------

    /** 基本工资 */
    private BigDecimal basicSalary;

    /** 岗位工资 */
    private BigDecimal postSalary;

    /** 绩效基数 */
    private BigDecimal performanceBase;

    /** 津贴项 */
    private List<SalaryAllowanceItem> allowances;

    /** 生效日期（缺省取流程预计入职日期） */
    private String effectiveDate;

    /** 定薪原因 */
    private String reason;
}
