package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入职 / 离职流程（同一张表，flow_type 区分，db.md §8.5，DDL: V7__hr.sql）。
 * <p>
 * 步骤不落本表数组，而是拆到 {@link HrFlowStep} 子表；出参由 Service 组装回 {@code steps[]}（对齐 Mock 契约）。
 * 离职「SETTLEMENT」步骤需创建财务结算单，引用回填 {@code settlementPayrollId/No/Amount}；
 * 人事域不直接依赖财务域，跨域经端口编排（见 {@code service/hr/port}，ADR-03）。
 */
@Data
@TableName("hr_flow")
public class HrFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程类型：ONBOARDING=入职，OFFBOARDING=离职 */
    private String flowType;

    /** 流程编号（活跃唯一） */
    private String flowNo;

    /** 候选人姓名（入职） */
    private String candidateName;

    /** 员工（离职；入职建档后回填） */
    private Long employeeId;

    /** 联系电话（入职） */
    private String phone;

    /** 性别：0=未知，1=男，2=女（入职） */
    private Integer gender;

    /** 学历（入职） */
    private String education;

    /** 部门（逻辑外键 department.id） */
    private Long deptId;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 岗位 */
    private String position;

    /** 角色：STAFF/STATION_ADMIN */
    private String role;

    /** 预计入职日期（入职） */
    private LocalDate expectedEntryDate;

    /** 离职类型：RESIGN/DISMISS/RETIRE（契约字段 type） */
    private String type;

    /** 离职原因 */
    private String reason;

    /** 最后工作日（离职） */
    private LocalDate lastWorkDate;

    /** 离职结算单 ID（SETTLEMENT 步骤回填，逻辑外键 payroll.id） */
    private Long settlementPayrollId;

    /** 离职结算单号快照 */
    private String settlementPayrollNo;

    /** 结算金额快照 */
    private BigDecimal settlementAmount;

    /** 离岗日期（LEAVE 步骤写入） */
    private LocalDate leaveDate;

    /** 流程状态：IN_PROGRESS/COMPLETED/REJECTED */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 驳回人姓名快照 */
    private String rejectedBy;

    /** 驳回时间 */
    private LocalDateTime rejectedTime;

    /** 当前待办步骤键（游标） */
    private String currentStepKey;

    /**
     * 业务来源（M-9/V18 新增）：{@code ADMIN}=后台创建，{@code SELF_REGISTER}=员工自助注册。
     * <p>
     * 与 {@code employee_registration.source}（注册渠道 {@code STAFF_H5}）语义不同、并存。
     */
    private String source;

    /** 备注 */
    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 流程创建人 id（Mock {@code toFlowVO.operatorId} 契约字段）。
     * <p>
     * V14 已为 {@code hr_flow} 补 {@code operator_id} / {@code operator_name} 两列
     * （`db/migration/mysql/V14__hr_flow_operator_columns.sql`），故 M4 起正式持久化：
     * 原 {@code @TableField(exist = false)} 已移除，两字段参与 insert/update/select。
     * 历史存量行两列为 NULL（出参为 null），不影响前端（前端未消费该字段）。
     */
    private Long operatorId;

    /** 流程创建人姓名快照（Mock {@code toFlowVO.operatorName}；V14 补列后持久化） */
    private String operatorName;
}
