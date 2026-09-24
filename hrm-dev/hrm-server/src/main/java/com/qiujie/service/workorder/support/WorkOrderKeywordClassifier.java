package com.qiujie.service.workorder.support;

import java.util.List;
import java.util.Map;

/**
 * 企微群消息「关键词 → 工单类型/优先级」判定（算法 S6 的第一处改造，见 algo-hrm-server.md §8.1）。
 * <p>
 * 两种模式：
 * <ul>
 *   <li>{@link #classifyByOrder} —— 基线，与 Mock {@code autoDispatch} 的「顺序首个命中」逐字等价；</li>
 *   <li>{@link #classifyByScore} —— S6 改造，按「关键词长度 × 特异度权重」加权取最高分，
 *       消除「宽泛词先于具体词」的误判（原型 12 条歧义样本 0.8333 → 1.0000）。</li>
 * </ul>
 * <b>默认走顺序命中</b>（开关 {@code hrm.algo.dispatch.keywordWeighted} 默认 false），保证「上线即无行为变化」；
 * 加权模式经用户裁定（Q5 口径）后开启，无需改本类。
 */
public final class WorkOrderKeywordClassifier {

    private WorkOrderKeywordClassifier() {
    }

    /** 规则载体（由 Service 从 work_order_dispatch_rule 装填；仅启用规则参与匹配） */
    public record Rule(long id, String keyword, int workOrderType, int priority, Long defaultAssigneeId) {
    }

    /** 判定结果：{@code ruleId}/{@code keyword} 为 null 表示未命中任何规则（走兜底类型/优先级） */
    public record Result(Long ruleId, int type, int priority, String keyword, Long defaultAssigneeId) {

        /** 是否命中规则 */
        public boolean matched() {
            return ruleId != null;
        }
    }

    /**
     * 基线：顺序首个「关键词被内容包含」的规则（严格对齐 Mock {@code db.dispatchRules.find(...)}，
     * 依赖调用方按 id 升序传入规则列表）。
     */
    public static Result classifyByOrder(String content, List<Rule> rules, int defaultType, int defaultPriority) {
        String text = content == null ? "" : content;
        if (rules != null) {
            for (Rule rule : rules) {
                if (rule.keyword() != null && !rule.keyword().isEmpty() && text.contains(rule.keyword())) {
                    return new Result(rule.id(), rule.workOrderType(), rule.priority(), rule.keyword(),
                            rule.defaultAssigneeId());
                }
            }
        }
        return new Result(null, defaultType, defaultPriority, null, null);
    }

    /**
     * S6 加权打分：{@code score = weight(keyword) × keyword.length()}，取最高分；<b>并列取先出现者</b>
     * （对齐原型 {@code w > best.w} 的严格大于语义）。未命中时同基线走兜底。
     */
    public static Result classifyByScore(String content, List<Rule> rules, Map<String, Double> keywordWeights,
                                         int defaultType, int defaultPriority) {
        String text = content == null ? "" : content;
        Rule best = null;
        double bestScore = 0;
        if (rules != null) {
            for (Rule rule : rules) {
                if (rule.keyword() == null || rule.keyword().isEmpty() || !text.contains(rule.keyword())) {
                    continue;
                }
                double score = weightOf(rule.keyword(), keywordWeights) * rule.keyword().length();
                if (best == null || score > bestScore) {
                    best = rule;
                    bestScore = score;
                }
            }
        }
        if (best == null) {
            return new Result(null, defaultType, defaultPriority, null, null);
        }
        return new Result(best.id(), best.workOrderType(), best.priority(), best.keyword(), best.defaultAssigneeId());
    }

    /** 关键词特异度权重：未配置项按 1.0（对齐原型 {@code keywordWeights[k] || 1}） */
    private static double weightOf(String keyword, Map<String, Double> keywordWeights) {
        if (keywordWeights == null) {
            return 1.0;
        }
        Double weight = keywordWeights.get(keyword);
        return weight == null ? 1.0 : weight;
    }
}
