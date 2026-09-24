package com.qiujie.service.workorder.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S6 多目标派单打分纯逻辑单测（algo-hrm-server.md §8；原型 {@code algo-scripts/s6-dispatch.mjs}）。
 * <p>
 * 边界覆盖（对齐 §8.1 单测边界清单）：① 空候选 ② 单候选 ③ 权重和为 0（取首个可用）
 * ⑤ SLA 阈值缺失（回落兜底） ⑥ 单员工驿站（同单候选） ⑧ 权重全 0 ⑨ 权重为负（拒绝）
 * 另覆盖：全员满负载、同分并列、高负载降权、就绪度对高优先级影响更大。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class WorkOrderDispatchScorerTest {

    private static final Map<Integer, Integer> SLA_HOURS = Map.of(0, 48, 1, 24, 2, 8);
    private static final int DEFAULT_SLA_HOURS = 24;
    private static final double SERVICE_HOURS = 1.0;
    /** 算法方案默认权重组（Q5 未裁定前） */
    private static final WorkOrderDispatchScorer.Weights DEFAULT_WEIGHTS =
            new WorkOrderDispatchScorer.Weights(4.0, 2.5, 1.5, 2.0);

    private static List<Long> rankIds(int priority, List<WorkOrderDispatchScorer.Candidate> candidates,
                                      WorkOrderDispatchScorer.Weights weights) {
        return WorkOrderDispatchScorer.rank(priority, candidates, weights, SLA_HOURS, DEFAULT_SLA_HOURS, SERVICE_HOURS)
                .stream().map(WorkOrderDispatchScorer.Score::employeeId).toList();
    }

    @Test
    @DisplayName("空候选：返回空列表 → 不指派（转人工），不抛异常")
    void emptyCandidates() {
        List<WorkOrderDispatchScorer.Score> ranked =
                WorkOrderDispatchScorer.rank(1, List.of(), DEFAULT_WEIGHTS, SLA_HOURS, DEFAULT_SLA_HOURS, SERVICE_HOURS);
        assertTrue(ranked.isEmpty());
        assertNull(WorkOrderDispatchScorer.select(ranked));
    }

    @Test
    @DisplayName("单候选（含单员工驿站）：直接选中该员工")
    void singleCandidate() {
        List<WorkOrderDispatchScorer.Candidate> one =
                List.of(new WorkOrderDispatchScorer.Candidate(9L, 0.0, 0, 0));
        assertEquals(9L, WorkOrderDispatchScorer.select(
                WorkOrderDispatchScorer.rank(1, one, DEFAULT_WEIGHTS, SLA_HOURS, DEFAULT_SLA_HOURS, SERVICE_HOURS)));
    }

    @Test
    @DisplayName("权重和为 0（全 0）：全部同分 → 保序取首个可用候选")
    void zeroWeightsPickFirst() {
        List<WorkOrderDispatchScorer.Candidate> candidates = List.of(
                new WorkOrderDispatchScorer.Candidate(1L, 0.9, 0, 0),
                new WorkOrderDispatchScorer.Candidate(2L, 0.1, 5, 9));
        WorkOrderDispatchScorer.Weights zero = new WorkOrderDispatchScorer.Weights(0, 0, 0, 0);
        List<Long> ids = rankIds(1, candidates, zero);
        assertEquals(List.of(1L, 2L), ids);
        assertEquals(1L, ids.get(0));
    }

    @Test
    @DisplayName("权重为负：拒绝（抛 IllegalArgumentException，调用方据此降级）")
    void negativeWeightRejected() {
        WorkOrderDispatchScorer.Weights negative = new WorkOrderDispatchScorer.Weights(-1.0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> WorkOrderDispatchScorer.rank(1,
                        List.of(new WorkOrderDispatchScorer.Candidate(1L, 0, 0, 0)),
                        negative, SLA_HOURS, DEFAULT_SLA_HOURS, SERVICE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderDispatchScorer.validateWeights(null));
    }

    @Test
    @DisplayName("全员满负载：负载项拉平，由技能/就绪度分出高低")
    void allFullLoadTieBrokenBySkill() {
        List<WorkOrderDispatchScorer.Candidate> candidates = List.of(
                new WorkOrderDispatchScorer.Candidate(1L, 0.10, 5, 5),
                new WorkOrderDispatchScorer.Candidate(2L, 0.90, 5, 5));
        List<Long> ids = rankIds(2, candidates, DEFAULT_WEIGHTS);
        assertEquals(2L, ids.get(0)); // 技能高者优先
    }

    @Test
    @DisplayName("同分并列：保序取先前出现者（员工 id 升序中的小者）")
    void equalScoreKeepsFirst() {
        List<WorkOrderDispatchScorer.Candidate> candidates = List.of(
                new WorkOrderDispatchScorer.Candidate(1L, 0.5, 2, 2),
                new WorkOrderDispatchScorer.Candidate(2L, 0.5, 2, 2));
        assertEquals(1L, rankIds(1, candidates, DEFAULT_WEIGHTS).get(0));
    }

    @Test
    @DisplayName("高负载降权：技能相同时，未完成量多者排后（负载均衡生效）")
    void loadBalancingPrefersIdle() {
        List<WorkOrderDispatchScorer.Candidate> candidates = List.of(
                new WorkOrderDispatchScorer.Candidate(1L, 0.0, 10, 10), // 忙
                new WorkOrderDispatchScorer.Candidate(2L, 0.0, 0, 0));   // 闲
        assertEquals(2L, rankIds(1, candidates, DEFAULT_WEIGHTS).get(0));
    }

    @Test
    @DisplayName("就绪度：高优先级工单（SLA 短）对等待更敏感，忙者被更重降权")
    void readinessMattersMoreForHighPriority() {
        List<WorkOrderDispatchScorer.Candidate> candidates = List.of(
                new WorkOrderDispatchScorer.Candidate(1L, 0.5, 0, 0),  // 空闲
                new WorkOrderDispatchScorer.Candidate(2L, 0.5, 0, 20)); // 等待 20h
        // 高优先级（SLA 8h）：忙者就绪度 = 1 − min(1, 20/8) = 0 → 空闲者胜
        assertEquals(1L, rankIds(2, candidates, DEFAULT_WEIGHTS).get(0));
        // 低优先级（SLA 48h）：忙者就绪度 = 1 − 20/48 > 0；技能/负载同分下仍由就绪度决定 → 仍空闲者胜
        assertEquals(1L, rankIds(0, candidates, DEFAULT_WEIGHTS).get(0));
    }

    @Test
    @DisplayName("SLA 阈值缺失：回落兜底小时数，仍返回确定排序（不抛异常）")
    void missingSlaFallsBack() {
        Map<Integer, Integer> partial = Map.of(1, 24);
        List<WorkOrderDispatchScorer.Candidate> candidates = List.of(
                new WorkOrderDispatchScorer.Candidate(1L, 0.2, 0, 0),
                new WorkOrderDispatchScorer.Candidate(2L, 0.8, 0, 0));
        List<WorkOrderDispatchScorer.Score> ranked = WorkOrderDispatchScorer.rank(
                2, candidates, DEFAULT_WEIGHTS, partial, DEFAULT_SLA_HOURS, SERVICE_HOURS);
        assertEquals(2L, ranked.get(0).employeeId());
    }

    @Test
    @DisplayName("分项分与总分自洽：total = wU·urgency + wS·skill + wL·load + wSp·readiness")
    void scoreDecomposition() {
        List<WorkOrderDispatchScorer.Candidate> candidates =
                List.of(new WorkOrderDispatchScorer.Candidate(1L, 0.4, 1, 2));
        WorkOrderDispatchScorer.Score score = WorkOrderDispatchScorer
                .rank(1, candidates, DEFAULT_WEIGHTS, SLA_HOURS, DEFAULT_SLA_HOURS, SERVICE_HOURS).get(0);
        // priority=1 → urgency=2/3；SLA=24；wait=2 → readiness=1−2/24；maxHandled=1 → load=0
        double readiness = 1 - 2.0 / 24;
        double expected = 4.0 * (2.0 / 3) * readiness + 2.5 * 0.4 + 1.5 * 0 + 2.0 * readiness;
        assertEquals(expected, score.total(), 1e-9);
        assertEquals(readiness, score.readiness(), 1e-9);
    }
}
