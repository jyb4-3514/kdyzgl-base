package com.qiujie.dto.workorder;

import lombok.Data;

/**
 * 企微自动派单规则维护请求（对齐 Mock {@code workOrder.updateDispatchRule} 的 body，仅 ADMIN）。
 * <p>
 * 所有字段均可选（部分更新）：未传即不改；显式传空 {@code defaultAssigneeId} 表示清空默认处理人。
 * {@code enabled} 声明为 {@code Object}：契约同时接受 {@code 0/1} 与 {@code true/false}
 * （Mock {@code ![0,1,true,false].includes(body.enabled)}），故由 Service 归一化后落库。
 */
@Data
public class DispatchRuleUpdateRequest {

    /** 关键词（1-20 字） */
    private String keyword;

    /** 命中后工单类型：1-4 */
    private Integer workOrderType;

    /** 命中后优先级：0-2 */
    private Integer priority;

    /** 默认处理人（须在职；传空表示清空） */
    private Long defaultAssigneeId;

    /** 启用：0/1 或 true/false */
    private Object enabled;
}
