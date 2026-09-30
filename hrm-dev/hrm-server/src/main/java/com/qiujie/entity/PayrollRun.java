package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动算薪运行记录与补跑幂等（db.md §8.6.7 / DDL: V20 + V21）。
 * <p>
 * 两个正交唯一键（方案 §3.3）：
 * <ul>
 *   <li>{@code uk_payroll_run_claim (station_id, claim_key)}：跨日终态占位——{@code RUNNING}/{@code SUCCESS}/
 *       终止类 {@code SKIPPED} 写 {@code claimKey=targetMonth}；{@code FAILED} 置 NULL 释放；</li>
 *   <li>{@code uk_attempt (station_id, target_month, attempt_date)}：日内一次硬防线（每自然日至多一行）。</li>
 * </ul>
 * 只增不删：{@code is_deleted} 业务永不置位（Q-DB-9）。{@code attempt_date} 为 {@code NOT NULL} 且无默认，
 * 应用层必须显式写入（由判定时区派生）。
 */
@Data
@TableName("payroll_run")
public class PayrollRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    /** 目标账期 yyyy-MM（恒为 dueAt 所在月） */
    private String targetMonth;

    /** 本次尝试的自然日（判定时区墙钟；uk_attempt 的闸门维度，恒有值） */
    private LocalDate attemptDate;

    /** 触发方式：AUTO=定时到点 / CATCH_UP=补跑 / MANUAL=手工触发 */
    private String triggerType;

    /** 本次应执行时刻（Asia/Shanghai 墙钟） */
    private LocalDateTime dueAt;

    /** 结果：RUNNING/SUCCESS/FAILED/SKIPPED */
    private String status;

    /** 跳过码：BLOCKED_9405/CONFIG_INVALID/DRAFT_PROTECTED */
    private String skipCode;

    /** 跳过原因（人类可读） */
    private String skipReason;

    /** 生成单据数 */
    private Integer generatedCount;

    /** 失败原因（服务端构造、脱敏、≤500） */
    private String failReason;

    /** 认领槽位 = targetMonth；RUNNING/SUCCESS/SKIPPED 写值，FAILED 置 NULL */
    private String claimKey;

    /** 手工触发人（MANUAL 时） */
    private Long operatorId;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
