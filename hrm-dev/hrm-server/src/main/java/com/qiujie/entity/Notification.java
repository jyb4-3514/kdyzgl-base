package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（db.md §8.2.1，DDL: V4__notification.sql）。
 * <p>
 * 一行一收件人的扇出结构：手工发布的 ALL/STATION/EMPLOYEE 范围在 Service 展开为多条记录，
 * 故本表只存最终接收人 {@code employeeId}，不存接收范围取值本身（范围快照写在 {@code publishScope}）。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人（逻辑外键 employee.id）；查询/已读一律以登录身份收口 */
    private Long employeeId;

    /** 类型：1=工单指派 2=工单流转 3=同步失败 4=系统公告 5=请假申请 6=请假结果 */
    private Integer type;

    /** 标题（1-100 字） */
    private String title;

    /** 内容（1-500 字） */
    private String content;

    /** 跳转类型：work_order / sync_task / leave（公告为空） */
    private String bizType;

    /** 跳转对象 ID */
    private Long bizId;

    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;

    /** 已读时间 */
    private LocalDateTime readTime;

    /** 来源：0=系统联动，1=手工发布 */
    private Integer isPublished;

    /** 发布人（手工发布时，逻辑外键 employee.id） */
    private Long publisherId;

    /** 发布人姓名快照 */
    private String publisherName;

    /** 发布范围：ALL / STATION / EMPLOYEE（系统联动为空） */
    private String publishScope;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
