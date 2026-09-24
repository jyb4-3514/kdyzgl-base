package com.qiujie.vo.workorder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单时间线条目出参（对齐 Mock {@code handle_log} 数组元素：time / action / operatorName / content）。
 */
@Data
public class HandleLogVO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime time;

    /** 动作：create/accept/resolve/close/reopen/assign/transfer/auto_dispatch */
    private String action;

    /** 操作人姓名快照（系统/企微来源为「企业微信采集」） */
    private String operatorName;

    /** 内容 */
    private String content;
}
