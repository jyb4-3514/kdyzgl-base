package com.qiujie.service.finance.support;

import com.qiujie.dto.finance.PayrollRuleItemRequest;
import com.qiujie.dto.finance.PayrollRuleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 计薪规则入参校验纯逻辑单测（文案对齐 Mock {@code validateRuleBody / validateItems}）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollRuleValidatorTest {

    @Test
    @DisplayName("新建：ruleName 缺失/过短 → 拒绝")
    void ruleNameRequired() {
        PayrollRuleRequest request = new PayrollRuleRequest();
        request.setItems(List.of(item()));
        assertEquals("规则名称长度须为 2-50", PayrollRuleValidator.validate(request, true));

        request.setRuleName("A");
        assertEquals("规则名称长度须为 2-50", PayrollRuleValidator.validate(request, true));
    }

    @Test
    @DisplayName("status / remark 越界 → 拒绝")
    void statusRemark() {
        PayrollRuleRequest request = new PayrollRuleRequest();
        request.setRuleName("标准规则");
        request.setItems(List.of(item()));
        request.setStatus(2);
        assertEquals("status 仅支持 0 / 1", PayrollRuleValidator.validate(request, true));

        request.setStatus(1);
        request.setRemark("x".repeat(201));
        assertEquals("备注不可超过 200 字", PayrollRuleValidator.validate(request, true));
    }

    @Test
    @DisplayName("items 缺失/空 → 拒绝")
    void itemsRequired() {
        PayrollRuleRequest request = new PayrollRuleRequest();
        request.setRuleName("标准规则");
        assertEquals("items 须为非空数组", PayrollRuleValidator.validate(request, true));
        request.setItems(List.of());
        assertEquals("items 须为非空数组", PayrollRuleValidator.validate(request, true));
    }

    @Test
    @DisplayName("规则项各字段非法 → 逐条拒绝")
    void itemFields() {
        assertEquals("规则项 key 长度须为 1-30",
                PayrollRuleValidator.validateItems(List.of(itemWithKey(null))));
        assertEquals("规则项 key 须为大写字母、数字与下划线",
                PayrollRuleValidator.validateItems(List.of(itemWithKey("basic"))));

        PayrollRuleItemRequest dup1 = itemWithKey("BASIC");
        PayrollRuleItemRequest dup2 = itemWithKey("BASIC");
        assertEquals("规则项 key 重复：BASIC", PayrollRuleValidator.validateItems(List.of(dup1, dup2)));

        PayrollRuleItemRequest badName = itemWithKey("BASIC");
        badName.setName("");
        assertEquals("规则项名称长度须为 1-20", PayrollRuleValidator.validateItems(List.of(badName)));

        PayrollRuleItemRequest badType = itemWithKey("BASIC");
        badType.setType("X");
        assertEquals("规则项类型仅支持 ADDITION / DEDUCTION", PayrollRuleValidator.validateItems(List.of(badType)));

        PayrollRuleItemRequest badSource = itemWithKey("BASIC");
        badSource.setSource("X");
        assertEquals("规则项来源仅支持 FIXED / ATTENDANCE / KPI / MANUAL",
                PayrollRuleValidator.validateItems(List.of(badSource)));

        PayrollRuleItemRequest badEnabled = itemWithKey("BASIC");
        badEnabled.setEnabled(2);
        assertEquals("规则项 enabled 仅支持 0 / 1", PayrollRuleValidator.validateItems(List.of(badEnabled)));
    }

    @Test
    @DisplayName("合法入参 → null（编辑时未传字段不校验）")
    void valid() {
        PayrollRuleRequest request = new PayrollRuleRequest();
        request.setRuleName("标准规则");
        request.setStatus(1);
        request.setRemark("备注");
        request.setItems(List.of(itemWithKey("BASIC")));
        assertNull(PayrollRuleValidator.validate(request, true));

        PayrollRuleRequest update = new PayrollRuleRequest();
        update.setStatus(0);
        assertNull(PayrollRuleValidator.validate(update, false));
    }

    @Test
    @DisplayName("工资单号生成：PAY/SET + 账期去横线 + 员工 id 左补零 4 位")
    void payrollNo() {
        assertEquals("PAY-202609-0012", PayrollNoGenerator.of("MONTHLY", "2026-09", 12L));
        assertEquals("SET-202601-0007", PayrollNoGenerator.of("SETTLEMENT", "2026-01", 7L));
        assertEquals("PAY-202609-12345", PayrollNoGenerator.of("MONTHLY", "2026-09", 12345L));
    }

    // ==================== 夹具 ====================

    private static PayrollRuleItemRequest item() {
        return itemWithKey("BASIC");
    }

    private static PayrollRuleItemRequest itemWithKey(String key) {
        PayrollRuleItemRequest item = new PayrollRuleItemRequest();
        item.setKey(key);
        item.setName("基本工资");
        item.setType("ADDITION");
        item.setSource("FIXED");
        item.setEnabled(1);
        item.setSortOrder(1);
        return item;
    }
}
