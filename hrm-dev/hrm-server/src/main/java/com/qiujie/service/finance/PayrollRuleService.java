package com.qiujie.service.finance;

import com.qiujie.dto.finance.PayrollRuleRequest;
import com.qiujie.vo.finance.PayrollRuleListVO;
import com.qiujie.vo.finance.PayrollRuleVO;

/**
 * 计薪规则服务（5 接口：list / create / detail / update / delete，仅 ADMIN）。
 * <p>
 * 规则维护是「配置驱动算薪」的入口：规则项声明「来源 + 参数」，删除受「已被工资单引用」保护（9403）。
 */
public interface PayrollRuleService {

    /** 规则列表（含规则项；按状态降序、id 升序） */
    PayrollRuleListVO listRules();

    /** 新建规则（含规则项；入参校验 400） */
    PayrollRuleVO createRule(PayrollRuleRequest request);

    /** 规则详情（不存在 9401） */
    PayrollRuleVO ruleDetail(Long id);

    /** 编辑规则（不存在 9401；items 传入即整体覆盖） */
    PayrollRuleVO updateRule(Long id, PayrollRuleRequest request);

    /** 删除规则（不存在 9401；已被工资单引用 9403，只能改为停用） */
    void deleteRule(Long id);
}
