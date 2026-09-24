package com.qiujie.vo.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知出参（对齐 Mock {@code notification.js#toNotificationVO}）。
 * <p>
 * 刻意<b>不含 employeeId</b>：通知一律以登录身份收口，出参暴露接收人无意义且徒增越权面。
 */
@Data
public class NotificationVO {

    private Long id;

    private Integer type;

    private String title;

    private String content;

    /** 跳转类型：work_order / sync_task / leave（公告为空） */
    private String bizType;

    private Long bizId;

    /** 是否已读（Mock 由 is_read===1 派生为布尔） */
    private Boolean isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 是否手工发布（is_published===1）；系统联动通知为 false 且无发布人信息 */
    private Boolean isPublished;

    private Long publisherId;

    private String publisherName;

    /** 发布范围：ALL / STATION / EMPLOYEE（系统联动为空） */
    private String publishScope;
}
