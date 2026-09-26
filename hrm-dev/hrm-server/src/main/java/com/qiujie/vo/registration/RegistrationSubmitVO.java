package com.qiujie.vo.registration;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * R-2 提交注册申请出参。
 * <p>
 * <b>恒定性（M-2 严格形态）</b>：无论手机号是否已注册，本响应字段逐字段一致
 * （无 {@code 9310}/{@code 2003} 差异）；{@code queryToken} 一期不启用、不在此返回。
 */
@Data
public class RegistrationSubmitVO {

    /** 申请编号（形如 RG-YYYYMMDD-0001） */
    private String applyNo;

    /** 申请状态（提交后为 SUBMITTED） */
    private String status;

    /** 申请时间 */
    private LocalDateTime createTime;
}
