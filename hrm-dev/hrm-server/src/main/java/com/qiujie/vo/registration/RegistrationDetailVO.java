package com.qiujie.vo.registration;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * R-3 申请单详情出参（ADMIN-only）。
 * <p>
 * <b>出参脱敏</b>：{@code phone}/{@code clientIp} 打码；<b>绝不返回</b> {@code passwordHash}/{@code queryTokenHash}
 * （凭据不回传，M-8/§5.1）。
 */
@Data
public class RegistrationDetailVO {

    /** 申请编号 */
    private String applyNo;

    /** 姓名 */
    private String realName;

    /** 手机号（脱敏，如 138****1234） */
    private String phone;

    /** 意向驿站 id（仅意向） */
    private Long intentStationId;

    /** 意向驿站名称（展示用；驿站不存在时为 null） */
    private String stationName;

    /** 意向岗位（仅意向） */
    private String intentPosition;

    /** 申请状态（SUBMITTED/APPROVED/REJECTED/EXPIRED） */
    private String status;

    /** 状态中文标签 */
    private String statusLabel;

    /** 驳回原因快照 */
    private String rejectReason;

    /** 申请时间 */
    private LocalDateTime createTime;

    /** 通过时间 */
    private LocalDateTime approveTime;

    /** 注册渠道来源（审计） */
    private String source;

    /** 提交来源 IP（脱敏） */
    private String clientIp;
}
