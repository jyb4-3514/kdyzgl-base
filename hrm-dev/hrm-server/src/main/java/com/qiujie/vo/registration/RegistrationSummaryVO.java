package com.qiujie.vo.registration;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * R-8 审批详情内的 {@code registration} 子对象（与 R-3 同构的子集）。
 * <p>
 * 供审批台展示注册来源与意向岗位；<b>不含</b>手机号/凭据等敏感字段（脱敏面在 R-3）。
 */
@Data
public class RegistrationSummaryVO {

    /** 申请编号 */
    private String applyNo;

    /** 意向岗位（仅意向） */
    private String intentPosition;

    /** 已同意的服务条款版本 */
    private String agreementVersion;

    /** 注册渠道来源（审计） */
    private String source;

    /** 申请时间 */
    private LocalDateTime createTime;

    /** 申请状态 */
    private String status;
}
