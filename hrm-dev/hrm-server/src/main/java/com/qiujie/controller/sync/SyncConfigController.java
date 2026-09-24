package com.qiujie.controller.sync;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.sync.StationSyncConfigSaveRequest;
import com.qiujie.service.sync.StationSyncConfigService;
import com.qiujie.vo.sync.SyncOverviewVO;
import com.qiujie.vo.sync.SyncStationConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 驿站采集配置与采集状态接口（M9，4 接口，Mock {@code routes/syncConfig.js}）。
 * <p>
 * 读操作对 ADMIN 与站长开放（站长进入只读视角），写操作仅 ADMIN。
 * <p>
 * <b>路由顺序</b>：本控制器的 {@code /sync/configs/{stationId}} 为变量路径，与
 * {@code SyncConfigCenterController} 的 {@code /sync/configs/global|export|import} 字面量路径共存；
 * Spring Boot 3 的 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），字面量端点天然优先，
 * <b>无需人为 @Order</b>（架构 §1.4.7；回归断言见 {@code controller/sync/SyncRouteOrderTest}）。
 * <p>
 * <b>越权口径</b>：{@code GET /{stationId}} 跨站 → <b>404</b>（不暴露他人资源存在性）；列表静默收敛。
 */
@RequireRoles({"ADMIN", "STATION_ADMIN"})
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncConfigController {

    private final StationSyncConfigService stationSyncConfigService;

    /** 采集状态总览（四态计数 + 每站一行） */
    @GetMapping("/overview")
    public Result<SyncOverviewVO> overview() {
        return Result.ok(stationSyncConfigService.overview());
    }

    /** 采集配置列表（非 ADMIN 由服务端收敛为本站） */
    @GetMapping("/configs")
    public Result<List<SyncStationConfigVO>> configs() {
        return Result.ok(stationSyncConfigService.list());
    }

    /** 单站配置（跨站 → 404；未配置 → 6002） */
    @GetMapping("/configs/{stationId}")
    public Result<SyncStationConfigVO> config(@PathVariable Long stationId) {
        return Result.ok(stationSyncConfigService.detail(stationId));
    }

    /** 保存采集配置（仅 ADMIN；驿站不存在 → 4001） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/configs/{stationId}")
    public Result<SyncStationConfigVO> saveConfig(@PathVariable Long stationId,
                                                  @RequestBody StationSyncConfigSaveRequest request) {
        return Result.ok(stationSyncConfigService.save(stationId, request));
    }
}
