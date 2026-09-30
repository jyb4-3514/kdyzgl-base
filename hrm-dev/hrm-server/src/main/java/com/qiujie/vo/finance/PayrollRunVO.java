package com.qiujie.vo.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动算薪运行记录出参（I-4 触发结果 / I-5 分页列表，api.md §4.12.18）。
 * <p>仅 ADMIN 可见，无字段裁剪。
 */
@Data
public class PayrollRunVO {

    private Long id;

    private Long stationId;

    /** 目标账期 yyyy-MM */
    private String targetMonth;

    /** 本次尝试的自然日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate attemptDate;

    /** 触发方式：AUTO / CATCH_UP / MANUAL */
    private String triggerType;

    /** 本次应执行时刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dueAt;

    /** 结果：RUNNING/SUCCESS/FAILED/SKIPPED */
    private String status;

    /** 跳过码：BLOCKED_9405/CONFIG_INVALID/DRAFT_PROTECTED（未跳过为 null） */
    private String skipCode;

    /** 跳过原因（人类可读） */
    private String skipReason;

    /** 生成单据数 */
    private Integer generatedCount;

    /**
     * 本次自动提交待审成功的单据数（B4a 修补）。
     * <p>仅 I-4 触发响应回填（运行内存态）；无持久列，I-5 分页列表返回 null。
     */
    private Integer submittedCount;

    /**
     * 本次自动提交未成功的单据数（B4a 修补）：这些单保持 {@code DRAFT}、已写
     * {@code payroll_log(AUTO_SUBMIT_SKIPPED)} 留痕，由管理员手工提交。
     * <p>仅 I-4 触发响应回填；无持久列，I-5 分页列表返回 null。
     */
    private Integer skippedCount;

    /** 失败原因（服务端脱敏、≤500） */
    private String failReason;

    /** 手工触发人姓名（MANUAL 时有值） */
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime finishTime;
}
