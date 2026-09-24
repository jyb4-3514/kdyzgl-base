package com.qiujie.vo.systemlog;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运行日志出参（api.md §7.5 白名单字段 + {@code count/firstTime/lastTime}）。
 * <p>
 * 刻意不含指纹：指纹是聚合用的服务端内部键，api.md §7.5 未列为契约字段（Mock {@code toClientLogVO} 的展开会
 * 顺带带出内部 {@code fingerprint}，此处以 §7.5 白名单为准，不外泄内部字段）。
 */
@Data
public class ClientLogVO {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime time;

    private String level;

    private String source;

    private Long employeeId;

    private String route;

    private String message;

    private String stack;

    private String method;

    private String path;

    private Integer status;

    private Integer code;

    private Integer duration;

    private String ua;

    /** 指纹去重窗口内累计出现次数 */
    private Integer count;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime firstTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastTime;
}
