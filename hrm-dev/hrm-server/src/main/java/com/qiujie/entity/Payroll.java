package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资单（月度 / 离职结算，db.md §8.6.3，DDL: V8__payroll.sql）。
 * <p>
 * 一行 = 一员工一账期一类型（{@code employee_id, month, bill_type} 由 Service 活跃查重）；
 * 六态状态机 DRAFT → PENDING_APPROVAL → APPROVED / REJECTED → PUBLISHED → CONFIRMED。
 * {@code ruleSnapshot} 存算薪时的规则快照（JSON 文本），保证规则被改/删后历史单仍可解释；
 * 该字段仅内部留存，不出现在 API 出参（与 Mock {@code toPayrollVO} 一致）。
 * <p>
 * 金额口径：{@code grossAmount} = 增项合计（应发），{@code netAmount} = 增项 − 扣项（实发净额）。
 */
@Data
@TableName("payroll")
public class Payroll {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工资单号（活跃唯一） */
    private String payrollNo;

    /** 员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 归属驿站（数据范围收敛用，逻辑外键 station.id） */
    private Long stationId;

    /** 账期 yyyy-MM */
    private String month;

    /** 单据类型：MONTHLY=月度工资单，SETTLEMENT=离职结算单 */
    private String billType;

    /** 计薪规则（逻辑外键 payroll_rule.id） */
    private Long ruleId;

    /** 规则名快照 */
    private String ruleName;

    /** 算薪时的规则快照（JSON 文本，历史可解释，不出参） */
    private String ruleSnapshot;

    /** 增项合计 */
    private BigDecimal additionTotal;

    /** 扣项合计 */
    private BigDecimal deductionTotal;

    /** 应发合计（=增项合计） */
    private BigDecimal grossAmount;

    /** 实发净额（应发 − 扣项） */
    private BigDecimal netAmount;

    /** 状态：DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUBLISHED/CONFIRMED/OBJECTED/PAID */
    private String status;

    /** 备注 */
    private String remark;

    /** 审核意见 */
    private String approveRemark;

    /** 审核人 */
    private Long approverId;

    /** 审核人姓名快照 */
    private String approverName;

    /** 审核时间 */
    private LocalDateTime approveTime;

    /** 发布人 */
    private Long publisherId;

    /** 发布人姓名快照 */
    private String publisherName;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 员工确认时间 */
    private LocalDateTime confirmTime;

    /** 员工异议原因 */
    private String objectionReason;

    /** 异议时间 */
    private LocalDateTime objectionTime;

    /** 确认发放人（逻辑外键 employee.id；V20 加列，已发放终态） */
    private Long paidById;

    /** 确认发放人姓名快照（V20 加列） */
    private String paidByName;

    /** 确认发放时间（V20 加列；NULL 表示未发放） */
    private LocalDateTime paidTime;

    /** 离职流程（结算单来源，逻辑外键 hr_flow.id） */
    private Long offboardingId;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
