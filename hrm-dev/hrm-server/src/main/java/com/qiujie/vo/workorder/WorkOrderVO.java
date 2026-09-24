package com.qiujie.vo.workorder;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单列表出参（对齐 Mock {@code toWorkOrderVO}）。
 * <p>
 * 字段顺序与语义按 Mock 逐条对齐；超时字段同时给出新名 {@code overdueUnhandled} 与其旧别名 {@code overSla}
 * （两者同值，Mock 并存；{@code overSla} 待 PC/移动端统一改用新名后删除）。
 * {@code handleLog[]} 由 {@code work_order_timeline} 组装回（替代 Mock 的 {@code handle_log} JSON 内嵌）。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class WorkOrderVO {

    private Long id;

    private String orderNo;

    /** 类型：1=包裹异常 2=设备故障 3=客户投诉 4=其他 */
    private Integer type;

    /** 状态：0=待处理 1=处理中 2=已解决 3=已关闭 */
    private Integer status;

    /** 优先级：0=低 1=中 2=高 */
    private Integer priority;

    private String title;

    private String content;

    /** 来源：MANUAL / AUTO_WECHAT */
    private String source;

    private Long stationId;

    private String stationName;

    private Long parcelId;

    private String waybillNo;

    private Long reporterId;

    private String reporterName;

    private Long assigneeId;

    private String assigneeName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime slaDeadline;

    /** 超时未处理（仅未处理完且已过 SLA 截止） */
    private boolean overdueUnhandled;

    /** 旧别名，与 overdueUnhandled 同值（兼容存量页面） */
    private boolean overSla;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime resolvedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closedTime;

    /** 处理时间线（由 work_order_timeline 组装，顺序与 Mock 留痕一致） */
    private List<HandleLogVO> handleLog;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
