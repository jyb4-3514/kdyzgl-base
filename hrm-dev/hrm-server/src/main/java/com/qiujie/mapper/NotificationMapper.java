package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Notification;

/**
 * 站内通知 Mapper（db.md §8.2.1）。
 * 本人列表/未读计数/全部已读的过滤条件由 Service 构造（数据所有权收口在 Service，不建物化外键）。
 */
public interface NotificationMapper extends BaseMapper<Notification> {
}
