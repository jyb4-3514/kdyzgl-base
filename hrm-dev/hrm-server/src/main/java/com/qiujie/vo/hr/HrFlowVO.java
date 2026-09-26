package com.qiujie.vo.hr;

import com.qiujie.vo.registration.RegistrationSummaryVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入职 / 离职流程出参（对齐 Mock {@code toFlowVO}，字段逐条一致）。
 * <p>
 * {@code phone} 脱敏；入职与离职共用同一 VO，按 {@code flowType} 取用各自字段（未用字段为 null）。
 */
@Data
public class HrFlowVO {

    private Long id;

    private String flowType;

    private String flowNo;

    private String candidateName;

    private Long employeeId;

    private String employeeName;

    private String phone;

    private Integer gender;

    private String education;

    private String educationLabel;

    private Long deptId;

    private Long stationId;

    private String stationName;

    private String position;

    private String role;

    private LocalDate expectedEntryDate;

    private String type;

    private String typeLabel;

    private String reason;

    private LocalDate lastWorkDate;

    private Long settlementPayrollId;

    private String settlementPayrollNo;

    private BigDecimal settlementAmount;

    private LocalDate leaveDate;

    private String remark;

    private String status;

    private String statusLabel;

    private String rejectReason;

    private String rejectedBy;

    private LocalDateTime rejectedTime;

    private String currentStepKey;

    private String currentStepName;

    private HrFlowProgressVO progress;

    private List<HrFlowStepVO> steps;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long operatorId;

    private String operatorName;

    /**
     * 注册申请子对象（R-8 详情新增，方案 §3.2）：
     * 仅自助注册流程的<b>详情</b>接口填充（列表不填充，避免 N+1）；无关联申请单时为 null。
     */
    private RegistrationSummaryVO registration;
}
