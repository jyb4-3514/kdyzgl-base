package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工自助注册申请单（注册「事实与凭据」载体，db.md / registration-design §2.2，DDL: V17）。
 * <p>
 * 与 {@link HrFlow}（审批载体）以 {@code flow_id} 1:1 关联，职责分离、互不污染。
 * <b>凭据卫生</b>：{@code passwordHash} 仅作合规留痕、非员工口令，进入任一终态
 * （APPROVED/REJECTED/EXPIRED）时须由 Service <b>同事务置 NULL</b>；
 * {@code queryTokenHash} 一期不启用、恒不写入（列保留）。
 */
@Data
@TableName("employee_registration")
public class EmployeeRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请编号（唯一，形如 RG-YYYYMMDD-0001；两段式生成：先占位插入取 id 再回填） */
    private String applyNo;

    /** 关联审批单（逻辑外键 hr_flow.id，提交时写入） */
    private Long flowId;

    /** 姓名（2-20，对齐既有 onboarding 校验口径） */
    private String realName;

    /** 手机号（出参脱敏；活跃唯一由 employee/hr_flow 侧约束收口） */
    private String phone;

    /** 注册自设密码 BCrypt 散列（仅合规留痕、非初始口令；终态置 NULL） */
    private String passwordHash;

    /** 意向驿站（逻辑外键 station.id；仅意向，非事实） */
    private Long applyStationId;

    /** 意向岗位（自由文本，对齐 hr_flow.position；仅意向） */
    private String applyPosition;

    /** 注册渠道来源（审计；一期仅 STAFF_H5） */
    private String source;

    /** 已同意的服务条款版本（合规留痕） */
    private String agreementVersion;

    /** 查询凭据 SHA-256（一期不启用，恒不写入；列保留供后续自助查询） */
    private String queryTokenHash;

    /** 状态：SUBMITTED/APPROVED/REJECTED/EXPIRED（CANCELLED 保留不用） */
    private String status;

    /** 驳回原因快照 */
    private String rejectReason;

    /** 通过后生成的员工（逻辑外键 employee.id） */
    private Long approvedEmployeeId;

    /** 通过时间 */
    private LocalDateTime approveTime;

    /** 取消时间（一期不用，随 R-4 取消而保留列） */
    private LocalDateTime cancelTime;

    /** 失效判定基准（create_time + N 天，惰性判定） */
    private LocalDateTime expireTime;

    /** 提交来源 IP（审计；出参脱敏） */
    private String clientIp;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
