package com.qiujie.dto.notification;

import lombok.Data;

import java.util.List;

/**
 * 手工发布通知入参（api.md §7.1 / Mock {@code notification.js#publish}，仅 ADMIN）。
 * <p>
 * 校验在 Service 内显式完成（文案需与 Mock 逐字一致：「标题长度须为 1-100 字符」等）。
 * 收件人一律取「在职且启用」员工；范围不合法统一回 9002。
 */
@Data
public class NotificationPublishRequest {

    /** 通知类型（缺省 4=系统公告）；放行 1~6 */
    private Integer type;

    /** 标题（1-100 字） */
    private String title;

    /** 内容（1-500 字） */
    private String content;

    /** 发布范围：ALL / STATION / EMPLOYEE */
    private String scope;

    /** STATION 范围必填：目标驿站 id */
    private Long stationId;

    /** EMPLOYEE 范围必填：目标员工 id 列表（非空） */
    private List<Long> employeeIds;
}
