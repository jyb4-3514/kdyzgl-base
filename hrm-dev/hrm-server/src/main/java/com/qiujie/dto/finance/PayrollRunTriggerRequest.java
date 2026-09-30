package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 手工触发自动算薪入参（I-4，api.md §4.12.18）。
 * <p>
 * <b>无 {@code force} 参数</b>（v1.5 复-2 裁定删除）：同日重复触发一律由 {@code uk_attempt} 唯一键硬拒绝回 {@code 9410}，
 * 不提供「同日再试 / 强制重跑已成功账期」能力。
 */
@Data
public class PayrollRunTriggerRequest {

    /** 驿站（必填） */
    private Long stationId;

    /** 目标账期 yyyy-MM（必填） */
    private String month;
}
