package com.qiujie.controller.station;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.station.StationRequest;
import com.qiujie.dto.station.StationStatusRequest;
import com.qiujie.service.station.StationService;
import com.qiujie.vo.common.IdVO;
import com.qiujie.vo.station.StationVO;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 驿站接口（api.md 4.5），全部仅 ADMIN。
 */
@RequireRoles({"ADMIN"})
@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    /** 4.5.1 驿站列表（全量，可选状态过滤） */
    @GetMapping
    public Result<List<StationVO>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(stationService.list(status));
    }

    /** 4.5.2 新增驿站 */
    @PostMapping
    public Result<IdVO> create(@Valid @RequestBody StationRequest request) {
        return Result.ok(stationService.create(request));
    }

    /** 4.5.3 编辑驿站 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody StationRequest request) {
        stationService.update(id, request);
        return Result.ok();
    }

    /** 4.5.4 启用/停用 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StationStatusRequest request) {
        stationService.changeStatus(id, request.getStatus());
        return Result.ok();
    }

    /** 4.5.5 删除驿站（有归属员工拒删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stationService.delete(id);
        return Result.ok();
    }
}
