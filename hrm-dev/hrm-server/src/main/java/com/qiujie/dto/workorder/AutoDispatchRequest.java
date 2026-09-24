package com.qiujie.dto.workorder;

import lombok.Data;

/**
 * 企业微信群消息自动派单请求（对齐 Mock {@code workOrder.autoDispatch} 的 body）。
 * <p>
 * <b>公开端点</b>（Mock {@code auth:false}）：真实企微回调由企微服务器发起、带签名而非本系统 JWT，
 * 故本接口不校验登录态，由接入层完成回调校验后再调用业务逻辑。
 * {@code sendTime} 为消息发送时间（字符串 yyyy-MM-dd HH:mm:ss），作为建单基准与 SLA 起算点；
 * 解析失败或为空时回落到当前时间（对齐 Mock {@code Number.isFinite(sendTs) ? ... : new Date()}）。
 */
@Data
public class AutoDispatchRequest {

    /** 来源群名称（可空） */
    private String groupName;

    /** 群消息发送人姓名（可空；不在系统内，不伪造上报人） */
    private String senderName;

    /** 群消息原文（必填，空则回 8006） */
    private String content;

    /** 消息发送时间（yyyy-MM-dd HH:mm:ss，可空） */
    private String sendTime;

    /** 归属驿站（缺省 1；须存在） */
    private Long stationId;
}
