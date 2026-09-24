package com.qiujie.dto.workorder;

import lombok.Data;

/**
 * 新建工单请求（对齐 Mock {@code workOrder.create} 的 body）。
 * <p>
 * 描述兼容 {@code content} / {@code description}，运单号兼容 {@code waybillNo} / {@code relatedWaybillNo}
 * （Mock 同义字段双收）；校验在 Service 逐条对齐 Mock 文案。
 */
@Data
public class WorkOrderCreateRequest {

    /** 类型：1=包裹异常 2=设备故障 3=客户投诉 4=其他 */
    private Integer type;

    /** 优先级：0=低 1=中 2=高 */
    private Integer priority;

    /** 标题（1-100 字） */
    private String title;

    /** 描述（≤500 字） */
    private String content;

    /** 描述别名（与 content 同义） */
    private String description;

    /** 归属驿站（仅 ADMIN 可指定，缺省 1；非 ADMIN 强制本人驿站） */
    private Long stationId;

    /** 关联包裹 id（可空） */
    private Long parcelId;

    /** 关联运单号（可空） */
    private String waybillNo;

    /** 关联运单号别名（与 waybillNo 同义） */
    private String relatedWaybillNo;

    /** 指定处理人（可空；非 ADMIN 只能指派本站在职员工） */
    private Long assigneeId;
}
