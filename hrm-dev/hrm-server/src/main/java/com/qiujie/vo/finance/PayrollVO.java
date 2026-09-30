package com.qiujie.vo.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工资单出参（对齐 Mock {@code toPayrollVO}）。
 * <p>
 * {@code actions} 由服务端下发的「当前状态允许动作」，前端不自行维护状态机，避免两边口径漂移。
 * 注：{@code rule_snapshot} 为内部留存字段，<b>不在此出参</b>（与 Mock 一致）。
 */
@Data
public class PayrollVO {

    private Long id;

    private String payrollNo;

    private Long employeeId;

    private String employeeName;

    private Long stationId;

    private String stationName;

    /** 账期 yyyy-MM */
    private String month;

    private String billType;

    private String billTypeLabel;

    private Long ruleId;

    private String ruleName;

    private List<PayrollItemVO> items;

    private BigDecimal additionTotal;

    private BigDecimal deductionTotal;

    private BigDecimal grossAmount;

    private BigDecimal netAmount;

    private String status;

    private String statusLabel;

    private String remark;

    private String approveRemark;

    private Long approverId;

    private String approverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime approveTime;

    private Long publisherId;

    private String publisherName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime confirmTime;

    private String objectionReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime objectionTime;

    /** 确认发放人（ADMIN 全量可见；I-8 后有效） */
    private Long paidById;

    /** 确认发放人姓名快照 */
    private String paidByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime paidTime;

    /** 离职流程 id（结算单来源） */
    private Long offboardingId;

    /** 当前状态允许动作（动作级子集：submit/approve/reject/publish/confirm/objection/pay） */
    private List<String> actions;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
