package com.qiujie.controller.notification;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.notification.NotificationPublishRequest;
import com.qiujie.dto.notification.NotificationQuery;
import com.qiujie.service.notification.NotificationService;
import com.qiujie.vo.notification.NotificationVO;
import com.qiujie.vo.notification.PublishResultVO;
import com.qiujie.vo.notification.UnreadCountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（api.md §7.1 / 架构 §6.3 M2，6 接口）。
 * <p>
 * 路由顺序：{@code /unread-count}、{@code /read-all}、{@code /publish} 为字面量段，Spring Boot 3 的
 * {@code PathPatternParser} 按模式特异性排序（字面量段 > 变量段），天然优先于 {@code /{id}}，<b>无需人为 @Order</b>
 * （结论见架构 §1.4.7；回归断言见 NotificationRouteOrderTest）。
 * <p>
 * 除手工发布（ADMIN）外，其余接口任何登录角色可访问，但数据一律以登录身份收口（只操作本人通知）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知分页（isRead 可选过滤） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping
    public Result<PageResult<NotificationVO>> page(@Valid NotificationQuery query) {
        return Result.ok(notificationService.page(query));
    }

    /** 我的未读数（字面量端点，优先于 /{id}） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.ok(notificationService.unreadCount());
    }

    /** 我的单条通知详情（非本人/不存在 → 9001） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/{id}")
    public Result<NotificationVO> detail(@PathVariable Long id) {
        return Result.ok(notificationService.detail(id));
    }

    /** 我的通知全部标记已读（字面量端点，幂等） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.readAll();
        return Result.ok();
    }

    /** 我的单条通知标记已读（非本人/不存在 → 9001） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/{id}/read")
    public Result<NotificationVO> markRead(@PathVariable Long id) {
        return Result.ok(notificationService.markRead(id));
    }

    /** 手工发布通知（仅 ADMIN），按范围扇出 */
    @RequireRoles({"ADMIN"})
    @PostMapping("/publish")
    public Result<PublishResultVO> publish(@RequestBody NotificationPublishRequest request) {
        return Result.ok(notificationService.publish(request));
    }
}
