package com.qiujie.service.finance.support;

import com.qiujie.entity.PayrollRule;
import com.qiujie.entity.PayrollRuleItem;
import com.qiujie.util.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则快照组装（纯逻辑）：把算薪当时的规则与规则项固化为 JSON，写入 {@code payroll.rule_snapshot}。
 * <p>
 * 为什么必须快照：规则是可热改的业务配置，改一次后历史单据若无快照就无法解释「当时为何是这个金额」；
 * 快照使「改了规则后历史结果不回改」（架构 §5.3 / 算法 §1.2）。
 * 快照只内部留存，不出现在 API 出参（对齐 Mock {@code toPayrollVO}）。
 */
public final class PayrollSnapshotBuilder {

    private PayrollSnapshotBuilder() {
    }

    /** 组装规则快照 JSON 文本（含全部项，含停用项，忠实反映算薪时配置） */
    public static String build(PayrollRule rule, List<PayrollRuleItem> items) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ruleId", rule == null ? null : rule.getId());
        snapshot.put("ruleName", rule == null ? null : rule.getRuleName());
        List<Map<String, Object>> itemList = new ArrayList<>();
        if (items != null) {
            for (PayrollRuleItem item : items) {
                if (item == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", item.getItemKey());
                row.put("name", item.getItemName());
                row.put("type", item.getItemType());
                row.put("source", item.getSource());
                row.put("params", item.getParams());
                row.put("enabled", item.getEnabled());
                row.put("sortOrder", item.getSortOrder());
                itemList.add(row);
            }
        }
        snapshot.put("items", itemList);
        return JsonUtil.write(snapshot);
    }
}
