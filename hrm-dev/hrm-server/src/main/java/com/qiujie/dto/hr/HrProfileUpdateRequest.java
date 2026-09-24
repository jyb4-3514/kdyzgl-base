package com.qiujie.dto.hr;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 人事档案编辑入参（白名单写入，对齐 Mock {@code saveProfile.WRITABLE}）。
 * <p>
 * {@code employeeId} / {@code leaveDate} 不接受请求体，防止越权改归属与伪造离职——故本 DTO 不含这两个字段。
 */
@Data
public class HrProfileUpdateRequest {

    /** 学历：MASTER/BACHELOR/COLLEGE/HIGH_SCHOOL */
    private String education;

    /** 合同类型：FIXED_TERM/NON_FIXED_TERM/INTERN/DISPATCH */
    private String contractType;

    /** 合同起始日（yyyy-MM-dd） */
    private String contractStart;

    /** 合同到期日（yyyy-MM-dd） */
    private String contractEnd;

    /** 试用期（月，0-12） */
    private Integer probationMonths;

    /** 试用期结束日（yyyy-MM-dd） */
    private String probationEnd;

    /** 转正日期（yyyy-MM-dd） */
    private String regularDate;

    /** 社保基数（≥0） */
    private BigDecimal socialSecurityBase;

    /** 紧急联系人姓名（2-20 字） */
    private String emergencyContactName;

    /** 紧急联系人电话 */
    private String emergencyContactPhone;

    /** 与本人关系 */
    private String emergencyContactRelation;

    /** 开户行（2-50 字） */
    private String bankName;

    /** 银行卡号（12-25 位数字） */
    private String bankAccount;
}
