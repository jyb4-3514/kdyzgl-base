package com.qiujie.controller.finance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.finance.PayrollSettingLogQuery;
import com.qiujie.dto.finance.StationPayrollSettingQuery;
import com.qiujie.dto.finance.StationPayrollSettingSaveRequest;
import com.qiujie.service.finance.StationPayrollSettingService;
import com.qiujie.vo.finance.PayrollSettingLogVO;
import com.qiujie.vo.finance.StationPayrollSettingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 驿站算薪配置接口（I-1 / I-2 / I-3 / I-9，api.md §4.12.16/§4.12.17）。
 * <p>全部 {@code ADMIN}（与 boss-h5 端准入 fail-closed 一致）。
 */
@RestController
@RequestMapping("/api/v1/finance/payroll-settings")
@RequiredArgsConstructor
public class PayrollSettingController {

    private final StationPayrollSettingService stationPayrollSettingService;

    /** 驿站列表 + 各站算薪配置（I-1） */
    @RequireRoles({"ADMIN"})
    @GetMapping
    public Result<List<StationPayrollSettingVO>> list(@Valid StationPayrollSettingQuery query) {
        return Result.ok(stationPayrollSettingService.list(query));
    }

    /** 单驿站算薪配置（I-2；未配置 9406、驿站不存在 4001） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/{stationId}")
    public Result<StationPayrollSettingVO> detail(@PathVariable Long stationId) {
        return Result.ok(stationPayrollSettingService.detail(stationId));
    }

    /** 保存算薪配置（I-3；每次保存写配置留痕，含 ENABLE/DISABLE） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/{stationId}")
    public Result<StationPayrollSettingVO> save(@PathVariable Long stationId,
                                                @RequestBody(required = false) StationPayrollSettingSaveRequest request) {
        return Result.ok(stationPayrollSettingService.save(stationId, request));
    }

    /** 配置变更历史（I-9；按 time 倒序分页） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/{stationId}/logs")
    public Result<PageResult<PayrollSettingLogVO>> logs(@PathVariable Long stationId,
                                                        @Valid PayrollSettingLogQuery query) {
        return Result.ok(stationPayrollSettingService.logs(stationId, query));
    }
}
