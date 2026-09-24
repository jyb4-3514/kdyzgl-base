package com.qiujie.vo.leave;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请假申请单出参（api.md §7.4）。
 * <p>
 * 派生标志 {@code canEdit/canCancel/canRevoke} 由服务端按登录人角色与状态算好，前端直接渲染动作按钮，
 * 不必各自再判一遍（与 Mock {@code toLeaveVO} 一致）。
 * 两级审批信息分槽存放：{@code approver*}（终审）与 {@code stationApprover*}（初审）互不覆盖。
 * 初审人/终审人/撤销人/撤回人姓名非本表列，出参时按对应 id 反查姓名快照填入。
 */
@Data
public class LeaveVO {

    private Long id;

    private Long employeeId;

    private String employeeName;

    private Long stationId;

    private String stationName;

    private String leaveType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    private String startPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String endPeriod;

    private String reason;

    private BigDecimal naturalDays;

    private BigDecimal countedDays;

    /** 计薪天数快照（仅终审通过后有值） */
    private LeaveSnapshotVO countedDaysSnapshot;

    private String status;

    /** 驳回阶段：null | STATION | BOSS */
    private String rejectStage;

    // 终审
    private Long approverId;

    private String approverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime approveTime;

    private String approveRemark;

    // 初审（两级分槽）
    private Long stationApproverId;

    private String stationApproverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime stationApproveTime;

    private String stationApproveRemark;

    // 撤销 / 撤回
    private Long cancelById;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime cancelTime;

    private Long revokerId;

    private String revokerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime revokeTime;

    private String revokeReason;

    /** 重提溯源：指向被驳回的原单 */
    private Long originId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime applyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /** 操作留痕 */
    private List<LeaveLogVO> handleLog;

    /** 派生：本人且待初审可编辑 */
    private boolean canEdit;

    /** 派生：本人且待审（初审/终审）可撤销 */
    private boolean canCancel;

    /** 派生：ADMIN 且已通过可撤回 */
    private boolean canRevoke;
}
