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
 * 人事档案（员工 1:1，db.md §8.5，DDL: V7__hr.sql）。
 * <p>
 * 离职判定以 {@code leaveDate} 为准（离岗步骤写入），而非 {@code employee.status}：
 * status=0 同时表示「禁用账号」与「已离职」，语义重叠，拿它判定会让被禁用账号连档案都改不了（Mock 注释已明确）。
 * <p>
 * {@code bankAccount} / {@code emergencyContactPhone} 为个人敏感信息，出参一律脱敏（需求 8）。
 */
@Data
@TableName("hr_profile")
public class HrProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工（逻辑外键 employee.id，1:1，活跃唯一） */
    private Long employeeId;

    /** 学历：MASTER/BACHELOR/COLLEGE/HIGH_SCHOOL */
    private String education;

    /** 合同类型：FIXED_TERM/NON_FIXED_TERM/INTERN/DISPATCH */
    private String contractType;

    /** 合同起始日 */
    private LocalDate contractStart;

    /** 合同到期日（到期预警派生） */
    private LocalDate contractEnd;

    /** 试用期（月） */
    private Integer probationMonths;

    /** 试用期结束日 */
    private LocalDate probationEnd;

    /** 转正日期 */
    private LocalDate regularDate;

    /** 社保基数 */
    private BigDecimal socialSecurityBase;

    /** 紧急联系人姓名 */
    private String emergencyContactName;

    /** 紧急联系人电话（出参脱敏） */
    private String emergencyContactPhone;

    /** 与本人关系 */
    private String emergencyContactRelation;

    /** 开户行 */
    private String bankName;

    /** 银行卡号（出参脱敏，保留末 4 位） */
    private String bankAccount;

    /** 离岗日期（离职判定依据，非 employee.status） */
    private LocalDate leaveDate;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
