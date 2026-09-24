package com.qiujie.controller.sync;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.sync.SyncConfigGlobalRequest;
import com.qiujie.dto.sync.SyncConfigImportRequest;
import com.qiujie.dto.sync.SyncConfigItemRequest;
import com.qiujie.dto.sync.SyncConfigOptionRequest;
import com.qiujie.service.sync.SyncConfigCenterService;
import com.qiujie.service.sync.SyncExportFile;
import com.qiujie.vo.sync.SyncConfigItemListVO;
import com.qiujie.vo.sync.SyncConfigItemVO;
import com.qiujie.vo.sync.SyncConfigOptionVO;
import com.qiujie.vo.sync.SyncGlobalConfigVO;
import com.qiujie.vo.sync.SyncImpactVO;
import com.qiujie.vo.sync.SyncImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 同步配置中心接口（M9，13 接口，仅 ADMIN，Mock {@code routes/syncConfigCenter.js}）。
 * <p>
 * 职责边界：本控制器是「配置元数据维护」（配置项 / 选项集 / 全局默认 / CSV 导入导出），
 * 与 {@code SyncConfigController}（驿站采集配置与状态看板，站长只读可见）严格分开 ——
 * 与 Mock 侧两文件拆分的理由一致，避免「站长只读看板」的越权口径变模糊。
 * <p>
 * <b>路由顺序（关键复核结论）</b>：Mock 依赖 {@code routes/index.js} 的注册顺序把配置中心整体排在
 * {@code syncConfigRoutes} 之前；<b>Spring 不需要也不应依赖注册顺序</b> ——
 * Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），
 * {@code /sync/configs/global|export|import} 天然优先于 {@code /sync/configs/{stationId}}，
 * 与控制器注册先后无关。回归断言见 {@code controller/sync/SyncRouteOrderTest}。
 */
@RequireRoles({"ADMIN"})
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncConfigCenterController {

    private final SyncConfigCenterService syncConfigCenterService;

    // ==================== 配置项 ====================

    /** 配置项定义 + 选项集（一次取全） */
    @GetMapping("/config-items")
    public Result<SyncConfigItemListVO> listItems() {
        return Result.ok(syncConfigCenterService.listItems());
    }

    /** 新增配置项（Key 重复 → 9502） */
    @PostMapping("/config-items")
    public Result<SyncConfigItemVO> createItem(@RequestBody SyncConfigItemRequest request) {
        return Result.ok(syncConfigCenterService.createItem(request));
    }

    /** 编辑配置项（不存在 → 9501；值类型锁定） */
    @PutMapping("/config-items/{itemKey}")
    public Result<SyncConfigItemVO> updateItem(@PathVariable String itemKey,
                                               @RequestBody SyncConfigItemRequest request) {
        return Result.ok(syncConfigCenterService.updateItem(itemKey, request));
    }

    /** 删除配置项（查询串 / body 的 confirm=true 时放行被引用删除；内置 → 9510） */
    @DeleteMapping("/config-items/{itemKey}")
    public Result<Void> deleteItem(@PathVariable String itemKey,
                                   @RequestParam(value = "confirm", required = false) String confirm) {
        syncConfigCenterService.deleteItem(itemKey, confirmFlag(confirm));
        return Result.ok();
    }

    /** 配置项删除影响面 */
    @GetMapping("/config-items/{itemKey}/impact")
    public Result<SyncImpactVO> itemImpact(@PathVariable String itemKey) {
        return Result.ok(syncConfigCenterService.itemImpact(itemKey));
    }

    // ==================== 选项 ====================

    /** 新增选项（配置项非单选型 → 400） */
    @PostMapping("/config-items/{itemKey}/options")
    public Result<SyncConfigOptionVO> createOption(@PathVariable String itemKey,
                                                   @RequestBody SyncConfigOptionRequest request) {
        return Result.ok(syncConfigCenterService.createOption(itemKey, request));
    }

    /** 编辑选项（不存在 → 9504） */
    @PutMapping("/config-items/{itemKey}/options/{optionKey}")
    public Result<SyncConfigOptionVO> updateOption(@PathVariable String itemKey, @PathVariable String optionKey,
                                                   @RequestBody SyncConfigOptionRequest request) {
        return Result.ok(syncConfigCenterService.updateOption(itemKey, optionKey, request));
    }

    /** 删除选项（内置 → 9510；被全局默认引用 → 9505；被覆盖且未确认 → 9505） */
    @DeleteMapping("/config-items/{itemKey}/options/{optionKey}")
    public Result<Void> deleteOption(@PathVariable String itemKey, @PathVariable String optionKey,
                                     @RequestParam(value = "confirm", required = false) String confirm) {
        syncConfigCenterService.deleteOption(itemKey, optionKey, confirmFlag(confirm));
        return Result.ok();
    }

    /** 选项删除影响面 */
    @GetMapping("/config-items/{itemKey}/options/{optionKey}/impact")
    public Result<SyncImpactVO> optionImpact(@PathVariable String itemKey, @PathVariable String optionKey) {
        return Result.ok(syncConfigCenterService.optionImpact(itemKey, optionKey));
    }

    // ==================== 全局默认 ====================

    /** 全局默认值（{ values }） */
    @GetMapping("/configs/global")
    public Result<SyncGlobalConfigVO> globalConfig() {
        return Result.ok(syncConfigCenterService.globalValues());
    }

    /** 保存全局默认（局部更新，逐项校验 9506/9507） */
    @PutMapping("/configs/global")
    public Result<SyncGlobalConfigVO> saveGlobalConfig(@RequestBody SyncConfigGlobalRequest request) {
        return Result.ok(syncConfigCenterService.saveGlobalValues(request));
    }

    // ==================== CSV 导入导出 ====================

    /** 导出（scope=ITEMS / ITEMS_GLOBAL / ALL / TEMPLATE），文件流响应 */
    @GetMapping("/configs/export")
    public void export(@RequestParam(value = "scope", required = false) String scope,
                       HttpServletResponse response) throws IOException {
        SyncExportFile file = syncConfigCenterService.export(scope);
        // 与 Mock shared/domain/csv.js 一致：CSV MIME + RFC 5987 文件名（避免中文乱码）
        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(file.filename(), StandardCharsets.UTF_8));
        response.getOutputStream().write(file.content());
        response.flushBuffer();
    }

    /** 导入（dryRun=true 只解析预览，false 才落库） */
    @PostMapping("/configs/import")
    public Result<SyncImportResultVO> importConfig(@RequestBody SyncConfigImportRequest request) {
        return Result.ok(syncConfigCenterService.importConfig(request));
    }

    /** 删除确认标记归一：'true' / '1' 视为已确认，其余（含 null）视为未确认 */
    private boolean confirmFlag(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}
