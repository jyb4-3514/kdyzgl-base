package com.qiujie.vo.hr;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人事档案出参（列表元素，对齐 Mock {@code toProfileVO}）。
 * <p>
 * {@code phone} / {@code emergencyContactPhone} / {@code bankAccount} 一律脱敏（需求 8）。
 * 详情端点额外带定薪摘要，见 {@link HrProfileDetailVO}。
 */
@Data
public class HrProfileVO {

    private Long employeeId;

    private String employeeName;

    private String username;

    private String phone;

    private String deptName;

    private String stationName;

    /** 岗位（取值 店员 / 站长 / 管理员；未登记为 null） */
    private String position;

    private LocalDate entryDate;

    private String education;

    private String educationLabel;

    private String contractType;

    private String contractTypeLabel;

    private LocalDate contractStart;

    private LocalDate contractEnd;

    private Integer probationMonths;

    private LocalDate probationEnd;

    private LocalDate regularDate;

    private BigDecimal socialSecurityBase;

    private String emergencyContactName;

    private String emergencyContactPhone;

    private String emergencyContactRelation;

    private String bankName;

    private String bankAccount;

    private LocalDate leaveDate;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
