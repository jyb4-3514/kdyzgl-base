package com.qiujie.controller.finance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.finance.PayrollRunQuery;
import com.qiujie.dto.finance.PayrollRunTriggerRequest;
import com.qiujie.service.finance.PayrollRunService;
import com.qiujie.vo.finance.PayrollRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动算薪运行接口（I-4 / I-5，api.md §4.12.18）。
 * <p>
 * I-4 为运维兜底入口（多驿站集中补跑）：<b>无 {@code force} 参数</b>，同日重复触发一律 {@code 9410}；
 * 命中生成层 {@code 9405} 时映射为 {@code SKIPPED} 结果（非错误）。全部 {@code ADMIN}。
 */
@RestController
@RequestMapping("/api/v1/finance/payroll-runs")
@RequiredArgsConstructor
public class PayrollRunController {

    private final PayrollRunService payrollRunService;

    /** 手工触发自动算薪（I-4） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/trigger")
    public Result<PayrollRunVO> trigger(@RequestBody(required = false) PayrollRunTriggerRequest request) {
        return Result.ok(payrollRunService.trigger(request));
    }

    /** 运行记录分页（I-5） */
    @RequireRoles({"ADMIN"})
    @GetMapping
    public Result<PageResult<PayrollRunVO>> list(@Valid PayrollRunQuery query) {
        return Result.ok(payrollRunService.list(query));
    }
}
