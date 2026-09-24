package com.qiujie.controller.sync;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.sync.SyncTaskQuery;
import com.qiujie.service.sync.SyncTaskService;
import com.qiujie.vo.sync.SyncTaskLogVO;
import com.qiujie.vo.sync.SyncTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同步任务接口（M9，5 接口，api.md / Mock {@code routes/syncTask.js}，架构 §6.2 P9）。
 * <p>
 * <b>角色门槛</b>：仅 ADMIN / STATION_ADMIN（STAFF 无同步状态页），逐条对齐 Mock {@code roles}。
 * <p>
 * <b>越权口径逐端点（不得统一）</b>：{@code GET /{id}} 与 {@code GET /{id}/logs} 跨站 → <b>404</b>；
 * {@code POST /{id}/trigger} 与 {@code POST /{id}/retry} 跨站 → <b>403</b>；列表由 L1 数据范围静默收敛。
 * 详情与日志的 404 语义差异由 Service 按端点传入的策略决定，不在本层统一。
 */
@RequireRoles({"ADMIN", "STATION_ADMIN"})
@RestController
@RequestMapping("/api/v1/sync-tasks")
@RequiredArgsConstructor
public class SyncTaskController {

    private final SyncTaskService syncTaskService;

    /** 任务列表（stationId / status / keyword / 分页；按创建时间倒序） */
    @GetMapping
    public Result<PageResult<SyncTaskVO>> list(@Valid SyncTaskQuery query) {
        return Result.ok(syncTaskService.page(query));
    }

    /** 批次日志（任务不存在 / 跨站 → 404） */
    @GetMapping("/{id}/logs")
    public Result<List<SyncTaskLogVO>> logs(@PathVariable Long id) {
        return Result.ok(syncTaskService.logs(id));
    }

    /** 任务详情（不存在 / 跨站 → 404） */
    @GetMapping("/{id}")
    public Result<SyncTaskVO> detail(@PathVariable Long id) {
        return Result.ok(syncTaskService.detail(id));
    }

    /** 手动触发（跨站 → 403；非待领取 → 6001） */
    @PostMapping("/{id}/trigger")
    public Result<SyncTaskVO> trigger(@PathVariable Long id) {
        return Result.ok(syncTaskService.trigger(id));
    }

    /** 失败重试（跨站 → 403；非失败 → 6001） */
    @PostMapping("/{id}/retry")
    public Result<SyncTaskVO> retry(@PathVariable Long id) {
        return Result.ok(syncTaskService.retry(id));
    }
}
