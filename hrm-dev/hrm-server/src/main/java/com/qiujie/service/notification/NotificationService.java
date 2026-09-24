package com.qiujie.service.notification;

import com.qiujie.common.PageResult;
import com.qiujie.dto.notification.NotificationPublishRequest;
import com.qiujie.dto.notification.NotificationQuery;
import com.qiujie.vo.notification.NotificationVO;
import com.qiujie.vo.notification.PublishResultVO;
import com.qiujie.vo.notification.UnreadCountVO;

/**
 * 站内通知服务（M2 notification，6 接口）。
 * <p>
 * 查询/已读/未读统计一律以登录身份收口，只操作当前登录人的通知（不接受前端传入 employeeId）。
 */
public interface NotificationService {

    /** 我的通知分页（可按 isRead 过滤） */
    PageResult<NotificationVO> page(NotificationQuery query);

    /** 我的未读数 */
    UnreadCountVO unreadCount();

    /** 我的单条通知详情（非本人/不存在一律 9001） */
    NotificationVO detail(Long id);

    /** 我的通知全部标记已读（幂等） */
    void readAll();

    /** 我的单条通知标记已读（非本人/不存在一律 9001） */
    NotificationVO markRead(Long id);

    /** 手工发布通知（ADMIN），按范围扇出，返回生成条数 */
    PublishResultVO publish(NotificationPublishRequest request);

    /**
     * 系统联动通知（{@code is_published=0}）：由业务域在关键节点投递给单个接收人。
     * <p>
     * 为什么单列：手工发布走 {@code publish}（按范围扇出、写发布人与范围），系统联动是一对一、
     * 无发布人、带业务跳转（{@code bizType}/{@code bizId}）；请假域（P7）以本方法投递类型 5/6，
     * 后续工单/同步失败复用同一出口（架构 §2.3 统一走 notification）。
     * 接收人为 null 时不落库（调用方负责留 {@code NOTIFY_SKIP} 排障痕迹）。
     *
     * @param employeeId 接收人（逻辑外键 employee.id）
     * @param type       通知类型（放行 1~6）
     * @param title      标题（1-100 字）
     * @param content    内容（1-500 字）
     * @param bizType    跳转类型（如 leave；无跳转传 null）
     * @param bizId      跳转对象 id
     */
    void sendSystem(Long employeeId, int type, String title, String content, String bizType, Long bizId);
}
