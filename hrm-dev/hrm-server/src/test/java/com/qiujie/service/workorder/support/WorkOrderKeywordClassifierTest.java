package com.qiujie.service.workorder.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键词判定单测（算法 S6 第一处改造，algo-hrm-server.md §8.1/§8.4；原型 {@code s6-dispatch.mjs}）。
 * <p>
 * 用原型 {@code keywordRules} + {@code keywordWeights} 与 12 条歧义样本对照「顺序命中（基线 0.8333）
 * vs 特异度加权（1.0000）」，并断言加权模式的并列打破取先出现者；另覆盖：无关键词命中走兜底类型 4 / 优先级 1、
 * Mock 规则集（DISPATCH_RULE_SEED）的顺序命中。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class WorkOrderKeywordClassifierTest {

    private static final int DEFAULT_TYPE = 4;
    private static final int DEFAULT_PRIORITY = 1;

    /** 对齐 Mock {@code DISPATCH_RULE_SEED}（顺序命中基线用例） */
    private static final List<WorkOrderKeywordClassifier.Rule> MOCK_RULES = List.of(
            new WorkOrderKeywordClassifier.Rule(1, "破损", 1, 2, null),
            new WorkOrderKeywordClassifier.Rule(2, "丢失", 1, 2, null),
            new WorkOrderKeywordClassifier.Rule(3, "投诉", 3, 1, null),
            new WorkOrderKeywordClassifier.Rule(4, "延迟", 4, 0, null),
            new WorkOrderKeywordClassifier.Rule(5, "错分", 1, 1, null));

    /** 对齐原型 {@code DISPATCH_DEFAULT_CONFIG.keywordRules}（等价性对照用例） */
    private static final List<WorkOrderKeywordClassifier.Rule> PROTOTYPE_RULES = List.of(
            new WorkOrderKeywordClassifier.Rule(1, "破损", 1, 1, null),
            new WorkOrderKeywordClassifier.Rule(2, "丢失", 1, 2, null),
            new WorkOrderKeywordClassifier.Rule(3, "故障", 2, 1, null),
            new WorkOrderKeywordClassifier.Rule(4, "投诉", 3, 2, null));

    /** 对齐原型 {@code keywordWeights} */
    private static final Map<String, Double> WEIGHTS = new LinkedHashMap<>(Map.of(
            "破损", 1.0, "丢失", 3.0, "故障", 1.0, "投诉", 2.5));

    @Test
    @DisplayName("顺序命中：与 Mock 完全一致，取首个「启用且关键词命中」的规则")
    void orderFirstMatch() {
        WorkOrderKeywordClassifier.Result hit =
                WorkOrderKeywordClassifier.classifyByOrder("包裹破损待核", MOCK_RULES, DEFAULT_TYPE, DEFAULT_PRIORITY);
        assertTrue(hit.matched());
        assertEquals(1L, hit.ruleId());
        assertEquals("破损", hit.keyword());
        assertEquals(1, hit.type());
        assertEquals(2, hit.priority());
    }

    @Test
    @DisplayName("无关键词命中：兜底类型 4 / 优先级 1（AUTO_DISPATCH_DEFAULT）")
    void fallbackWhenNoKeyword() {
        WorkOrderKeywordClassifier.Result none =
                WorkOrderKeywordClassifier.classifyByOrder("今天天气不错", MOCK_RULES, DEFAULT_TYPE, DEFAULT_PRIORITY);
        assertFalse(none.matched());
        assertNull(none.keyword());
        assertEquals(DEFAULT_TYPE, none.type());
        assertEquals(DEFAULT_PRIORITY, none.priority());

        // 两条路径（顺序/加权）的兜底必须一致
        WorkOrderKeywordClassifier.Result noneByScore =
                WorkOrderKeywordClassifier.classifyByScore("今天天气不错", MOCK_RULES, WEIGHTS, DEFAULT_TYPE, DEFAULT_PRIORITY);
        assertEquals(DEFAULT_TYPE, noneByScore.type());
        assertEquals(DEFAULT_PRIORITY, noneByScore.priority());
    }

    @Test
    @DisplayName("S6 加权：12 条歧义样本顺序 0.8333 → 加权 1.0000（与原型一致）")
    void weightedBeatsOrderOnAmbiguousSamples() {
        // 真值口径 = 「配置中最具体（权重最高）的命中关键词」对应类型（原型 truthOf）
        String[] contents = {
                "包裹破损待核", "包裹破损且客户投诉", "包裹丢失待查", "扫码枪故障", "货架损坏报修",
                "客户投诉取件慢", "投诉包裹丢失", "门禁故障", "门禁故障引起客户投诉",
                "包裹丢失同时货架损坏", "其他异常需人工确认", "现场情况不明"};
        int[] truth = {1, 3, 1, 2, 4, 3, 1, 2, 3, 1, 4, 4};

        int orderHit = 0;
        int scoreHit = 0;
        List<String> orderMiss = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            int orderType = WorkOrderKeywordClassifier
                    .classifyByOrder(contents[i], PROTOTYPE_RULES, DEFAULT_TYPE, DEFAULT_PRIORITY).type();
            int scoreType = WorkOrderKeywordClassifier
                    .classifyByScore(contents[i], PROTOTYPE_RULES, WEIGHTS, DEFAULT_TYPE, DEFAULT_PRIORITY).type();
            if (orderType == truth[i]) {
                orderHit++;
            } else {
                orderMiss.add(contents[i]);
            }
            if (scoreType == truth[i]) {
                scoreHit++;
            }
        }
        assertEquals(10, orderHit);   // 0.8333
        assertEquals(12, scoreHit);   // 1.0000
        assertTrue(orderMiss.contains("包裹破损且客户投诉"));   // 宽泛词「破损」抢先
        assertTrue(orderMiss.contains("门禁故障引起客户投诉")); // 宽泛词「故障」抢先
    }

    @Test
    @DisplayName("加权并列：等分取先出现者（严格大于才替换）")
    void weightedTieKeepsFirst() {
        // 两条规则关键词等长且权重相同 → 同分 → 取先出现者（id 小者）
        List<WorkOrderKeywordClassifier.Rule> tie = List.of(
                new WorkOrderKeywordClassifier.Rule(1, "破损", 1, 0, null),
                new WorkOrderKeywordClassifier.Rule(2, "投诉", 2, 1, null));
        Map<String, Double> equal = Map.of("破损", 2.0, "投诉", 2.0);
        WorkOrderKeywordClassifier.Result result =
                WorkOrderKeywordClassifier.classifyByScore("破损与投诉", tie, equal, DEFAULT_TYPE, DEFAULT_PRIORITY);
        assertEquals(1L, result.ruleId());
    }

    @Test
    @DisplayName("加权：默认命中人随规则透出（供 S6 无候选时回落）")
    void carriesDefaultAssignee() {
        List<WorkOrderKeywordClassifier.Rule> rules = List.of(
                new WorkOrderKeywordClassifier.Rule(7, "破损", 1, 2, 33L));
        WorkOrderKeywordClassifier.Result result =
                WorkOrderKeywordClassifier.classifyByOrder("包裹破损", rules, DEFAULT_TYPE, DEFAULT_PRIORITY);
        assertEquals(33L, result.defaultAssigneeId());
    }
}
