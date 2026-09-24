package com.qiujie.service.workorder.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * S6 · 工单多目标派单（加权标量化，algo-hrm-server.md §8；原型 {@code algo-scripts/s6-dispatch.mjs}）。
 * <p>
 * 目标函数（与原型逐式等价）：
 * <pre>
 *   score(e) = wUrgency · priorityUrgency · readiness(e)
 *            + wSkill   · skillShare(e, type)
 *            + wLoad    · (1 − handled(e) / maxHandled)
 *            + wSpeed   · readiness(e)
 *   readiness(e)   = 1 − min(1, max(0, waitHours(e)) / slaHours(priority))
 *   priorityUrgency = (priority + 1) / 3      // 低=1/3、中=2/3、高=1
 *   argmax_e score(e) → 派单；无候选 → 不指派（转人工）
 * </pre>
 * <b>纯函数</b>（不依赖 Spring / DB），故可离线单测；三个信号由 Service 从库中算出后传入：
 * {@code skillShare}（技能画像）、{@code handled}（负载窗口内接单量）、{@code openLoad}（未完成量 → 等待代理）。
 * <p>
 * <b>权重一律外置</b>（{@code hrm.algo.dispatch.weights.*}），Q5 未裁定前默认取算法方案默认组；
 * SLA 阈值取 {@code hrm.algo.dispatch.slaHours}。权重为负一律拒绝（调用方捕获后降级为「不指派」）。
 * <p>
 * <b>并列打破</b>：等分时保序（{@code List.sort} 稳定），即员工 id 升序中先出现者优先——与原型
 * 「严格大于才替换 best」等价。
 */
public final class WorkOrderDispatchScorer {

    private WorkOrderDispatchScorer() {
    }

    /** 候选员工信号（Service 从 work_order / employee 聚合） */
    public record Candidate(long employeeId, double skillShare, int handled, int openLoad) {
    }

    /** 打分结果（分项分保留，供派单理由/排障展示） */
    public record Score(long employeeId, double total, double urgency, double skill, double load, double readiness) {
    }

    /** 多目标权重（{@code hrm.algo.dispatch.weights.*}） */
    public record Weights(double urgency, double skill, double load, double speed) {
    }

    /**
     * 候选打分并排序（降序）。
     *
     * @param priority            工单优先级（0/1/2）
     * @param candidates          候选员工信号（调用方按 id 升序传入，保证并列打破口径稳定）
     * @param weights             多目标权重
     * @param slaHours            按优先级 SLA 小时（{@code hrm.algo.dispatch.slaHours}）
     * @param defaultSlaHours     SLA 键缺失时的兜底小时数
     * @param estimatedServiceHours 单件预估处理时长（就绪度等待代理：wait = openLoad × 本值）
     * @return 降序结果；无候选返回空列表
     * @throws IllegalArgumentException 权重缺失或为负（调用方按「算法不可用」降级）
     */
    public static List<Score> rank(int priority, List<Candidate> candidates, Weights weights,
                                   Map<Integer, Integer> slaHours, int defaultSlaHours,
                                   double estimatedServiceHours) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        validateWeights(weights);

        int sla = WorkOrderSlaPolicy.resolveHours(priority, slaHours, defaultSlaHours);
        double priorityUrgency = (priority + 1) / 3.0;
        int maxHandled = candidates.stream().mapToInt(Candidate::handled).max().orElse(0);
        double serviceHours = Math.max(0, estimatedServiceHours);

        List<Score> scores = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            // 就绪度：等待越久越低；SLA 越大（低优先级）等待影响越小
            double waitHours = Math.max(0, candidate.openLoad() * serviceHours);
            double readiness = sla > 0
                    ? 1 - Math.min(1, waitHours / sla)
                    : (waitHours <= 0 ? 1 : 0);
            // 负载：归一化到当前候选集最大接单量；全为 0 时该项中性（=1）
            double load = maxHandled > 0 ? 1 - (double) candidate.handled() / maxHandled : 1;
            double total = weights.urgency() * priorityUrgency * readiness
                    + weights.skill() * candidate.skillShare()
                    + weights.load() * load
                    + weights.speed() * readiness;
            scores.add(new Score(candidate.employeeId(), total, priorityUrgency * readiness,
                    candidate.skillShare(), load, readiness));
        }
        // List.sort 稳定：等分保序（先出现者优先），等价于原型「严格大于才替换」的并列打破
        scores.sort(Comparator.comparingDouble(Score::total).reversed());
        return scores;
    }

    /** 取最高分候选 id；无候选返回 null（调用方据此「不指派、转人工」） */
    public static Long select(List<Score> ranked) {
        return ranked == null || ranked.isEmpty() ? null : ranked.get(0).employeeId();
    }

    /** 权重非负校验（算法边界清单第 ⑨ 项：负权重须拒绝） */
    public static void validateWeights(Weights weights) {
        if (weights == null) {
            throw new IllegalArgumentException("派单权重缺失（hrm.algo.dispatch.weights.*）");
        }
        if (weights.urgency() < 0 || weights.skill() < 0 || weights.load() < 0 || weights.speed() < 0) {
            throw new IllegalArgumentException("派单权重不得为负（hrm.algo.dispatch.weights.*）");
        }
    }
}
