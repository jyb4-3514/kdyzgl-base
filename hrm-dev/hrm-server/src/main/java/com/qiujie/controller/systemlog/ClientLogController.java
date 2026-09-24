package com.qiujie.controller.systemlog;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.systemlog.ClientLogIngestRequest;
import com.qiujie.dto.systemlog.ClientLogQuery;
import com.qiujie.service.systemlog.ClientLogService;
import com.qiujie.vo.systemlog.ClientLogClearVO;
import com.qiujie.vo.systemlog.ClientLogIngestVO;
import com.qiujie.vo.systemlog.ClientLogPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前端运行日志接口（api.md §7.1 #14~#16 / 架构 §6.3 M1，3 接口）。
 * <p>
 * 上报端点不限角色（任何端都可能出错，含未登录页面脚本异常——但契约要求登录态，故仍需认证）；
 * 查看与清空仅 ADMIN。
 */
@RestController
@RequestMapping("/api/v1/system/client-logs")
@RequiredArgsConstructor
public class ClientLogController {

    private final ClientLogService clientLogService;

    /** #14 批量上报（≤100 条，任意登录角色） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping
    public Result<ClientLogIngestVO> ingest(@RequestBody ClientLogIngestRequest request) {
        return Result.ok(clientLogService.ingest(request));
    }

    /** #15 分页查询 + counts（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @GetMapping
    public Result<ClientLogPageVO> page(@Valid ClientLogQuery query) {
        return Result.ok(clientLogService.page(query));
    }

    /** #16 清空（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/clear")
    public Result<ClientLogClearVO> clear() {
        return Result.ok(clientLogService.clear());
    }
}
