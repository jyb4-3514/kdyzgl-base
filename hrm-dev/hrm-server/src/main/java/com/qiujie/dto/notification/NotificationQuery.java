package com.qiujie.dto.notification;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的通知查询条件（api.md §7.1，查询参数绑定）。
 * <p>
 * 继承 {@link PageQuery}；数据范围一律以登录身份收口（只查本人），<b>不接收</b>前端传入的 employeeId/stationId。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {

    /** 已读筛选：0=未读，1=已读；缺省或空串表示不筛选（对齐 Mock 的 isRead !== '' 判定） */
    private Integer isRead;
}
