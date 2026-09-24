package com.qiujie.controller.finance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.finance.PayrollRuleRequest;
import com.qiujie.service.finance.PayrollRuleService;
import com.qiujie.vo.finance.PayrollRuleListVO;
import com.qiujie.vo.finance.PayrollRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计薪规则接口（5 接口，全部仅 ADMIN；Mock {@code routes/finance.js}，架构 §6.2 P6）。
 * <p>
 * 规则列表不分页（对齐 Mock {@code { list }}）；详情与编辑/删除的口径一致——规则不存在一律 9401。
 */
@RestController
@RequestMapping("/api/v1/finance/payroll-rules")
@RequiredArgsConstructor
public class PayrollRuleController {

    private final PayrollRuleService payrollRuleService;

    /** 规则列表（含规则项） */
    @RequireRoles({"ADMIN"})
    @GetMapping
    public Result<PayrollRuleListVO> list() {
        return Result.ok(payrollRuleService.listRules());
    }

    /** 新建规则 */
    @RequireRoles({"ADMIN"})
    @PostMapping
    public Result<PayrollRuleVO> create(@RequestBody(required = false) PayrollRuleRequest request) {
        return Result.ok(payrollRuleService.createRule(request));
    }

    /** 规则详情（不存在 9401） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/{id}")
    public Result<PayrollRuleVO> detail(@PathVariable Long id) {
        return Result.ok(payrollRuleService.ruleDetail(id));
    }

    /** 编辑规则（items 传入即整体覆盖） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/{id}")
    public Result<PayrollRuleVO> update(@PathVariable Long id, @RequestBody(required = false) PayrollRuleRequest request) {
        return Result.ok(payrollRuleService.updateRule(id, request));
    }

    /** 删除规则（已被工资单引用 9403，只能改为停用） */
    @RequireRoles({"ADMIN"})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        payrollRuleService.deleteRule(id);
        return Result.ok();
    }
}
