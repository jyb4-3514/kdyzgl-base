package com.qiujie.dto.hr;

import lombok.Data;

/**
 * 发起入职流程入参（对齐 Mock {@code createOnboarding} + routes/hr.js 校验）。
 */
@Data
public class HrOnboardingCreateRequest {

    /** 候选人姓名（2-20 字） */
    private String candidateName;

    /** 联系电话（11 位手机号） */
    private String phone;

    /** 性别：0=未知，1=男，2=女 */
    private Integer gender;

    /** 学历 */
    private String education;

    /** 部门（可选） */
    private Long deptId;

    /** 驿站（可选） */
    private Long stationId;

    /** 岗位（可选） */
    private String position;

    /** 预计入职日期（yyyy-MM-dd，缺省今天） */
    private String expectedEntryDate;

    /** 备注（≤200 字） */
    private String remark;
}
