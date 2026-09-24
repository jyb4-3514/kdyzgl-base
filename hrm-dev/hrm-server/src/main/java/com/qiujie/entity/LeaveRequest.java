package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假申请单（db.md §7.1 / DDL: V9__leave.sql）。
 * <p>
 * 两级审批<b>分槽存放</b>：{@code stationApprover*}（初审）与 {@code approver*}（终审）分列，
 * 避免终审覆盖初审，让「谁初审的」永久可查。
 * <p>
 * {@code countedDaysSnapshot} 落 JSON 文本（与 P6 {@code payroll.ruleSnapshot} 同法：实体存 String、出参解析成对象），
 * 使排班事后变更不影响已出账口径。本表<b>无</b> {@code is_deleted} / {@code create_time} 列（DDL 未定义），
 * 故不加 {@code @TableLogic}；{@code update_time} 由应用层填充。
 * <p>
 * 注意：初审人姓名 / 终审人姓名 / 撤销人姓名 / 撤回人姓名<b>非本表列</b>，出参 VO 时按对应 id 反查姓名快照。
 */
@Data
@TableName("leave_request")
public class LeaveRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人（逻辑外键 employee.id） */
    private Long employeeId;

    /** 申请时归属驿站（初审站长判定依据） */
    private Long stationId;

    /** 假别：ANNUAL/PERSONAL/SICK/COMPENSATORY/MARRIAGE/MATERNITY/PATERNITY/BEREAVEMENT/OTHER */
    private String leaveType;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 开始半天粒度：AM / PM */
    private String startPeriod;

    /** 结束半天粒度：AM / PM */
    private String endPeriod;

    /** 请假事由（2-200 字） */
    private String reason;

    /** 自然天数（半天粒度 0.5） */
    private BigDecimal naturalDays;

    /** 申请时预估计薪天数（逐日查排班） */
    private BigDecimal countedDays;

    /** 终审通过时的计薪天数快照 JSON：{naturalDays, countedDays, scheduleDigest} */
    private String countedDaysSnapshot;

    /** 状态：PENDING_STATION/PENDING_BOSS/APPROVED/REJECTED/CANCELLED/REVOKED */
    private String status;

    /** 驳回阶段：STATION/BOSS（仅 REJECTED 有值） */
    private String rejectStage;

    /** 驳回后重提指向的原单（逻辑外键 leave_request.id） */
    private Long originId;

    /** 初审人（逻辑外键 employee.id） */
    private Long stationApproverId;

    /** 初审时间 */
    private LocalDateTime stationApproveTime;

    /** 初审意见 */
    private String stationApproveRemark;

    /** 终审人（逻辑外键 employee.id） */
    private Long approverId;

    /** 终审时间 */
    private LocalDateTime approveTime;

    /** 终审意见 */
    private String approveRemark;

    /** 撤销人（申请人本人） */
    private Long cancelById;

    /** 撤销时间 */
    private LocalDateTime cancelTime;

    /** 撤回人（审批人） */
    private Long revokerId;

    /** 撤回时间 */
    private LocalDateTime revokeTime;

    /** 撤回原因 */
    private String revokeReason;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 更新时间（应用层维护） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
