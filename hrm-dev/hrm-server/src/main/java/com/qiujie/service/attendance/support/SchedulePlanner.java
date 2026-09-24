package com.qiujie.service.attendance.support;

import java.util.ArrayList;
import java.util.List;

/**
 * S3 · 考勤排班生成：贪心构造 + 模拟退火（与离线原型 {@code algo-scripts/s3-schedule.mjs} 1:1 对应）。
 * <p>
 * 目标函数（罚函数式，权重全部外置 {@code hrm.algo.schedule.weights.*}）：
 * <pre>
 * J = wMinStaff · Σ max(0, minPerShift − headcount(d,s))
 *   + wConsecutive · (#连续工作超限次数)
 *   + wCoverageDeficit · Σ 缺口人数
 *   + wShiftBalance · Σ_e Var(该员工各班长度的方差)
 *   + wRestSpread · Std(各员工轮休天数)
 * </pre>
 * 硬约束违反（每日每班最少在岗、连续工作上限）罚权远大于软约束（架构 §11.3），保证「可行解优先」。
 * <p>
 * 为什么用罚函数 + 局部搜索而非 CP 求解器：规模极小（n≤50、T≤31、s=3）且结果需可解释；
 * 无解（人力不足）时返回<b>贪心解 + 违规清单</b>，不抛异常（失败降级硬要求）。
 * <p>
 * 维度约定：{@code shift[employee][day]} = 班次下标；{@code -1} 表示轮休。
 * 复杂度：贪心 O(D·E·S)、评估 O(D·E)、退火 O(iter·D·E)。
 */
public final class SchedulePlanner {

    /** 固定随机种子（与原型 s3-schedule.mjs {@code localSearch(..., 0x1f2e3d4c)} 一致，保证可复现） */
    public static final long DEFAULT_SEED = 0x1f2e3d4cL;

    /**
     * 贪心阶段「补缺口」的优先级放大系数。
     * 为什么需要：先保证每班达到 minPerShift，再谈员工班次均衡；放大系数使缺口优先于均衡偏好。
     * TODO(扩展): 若算法参数表增列该键，改为 {@code hrm.algo.schedule.greedyDeficitScale} 外置。
     */
    private static final int GREEDY_DEFICIT_SCALE = 100;

    private SchedulePlanner() {
    }

    // ==================== 参数与结果模型 ====================

    /** 约束与权重（来源：{@code hrm.algo.schedule.*}） */
    public record Config(int minPerShift, int maxConsecutiveWork, int restCycleDays,
                         double wMinStaff, double wConsecutive, double wCoverageDeficit,
                         double wShiftBalance, double wRestSpread,
                         int saIterations, double saInitialTemp, double saCooling) {
    }

    /** 排班解（{@code shift[employee][day]}：≥0 为班次下标，-1 为轮休） */
    public record Solution(int[][] shift) {
        public Solution copy() {
            int[][] clone = new int[shift.length][];
            for (int e = 0; e < shift.length; e++) {
                clone[e] = shift[e].clone();
            }
            return new Solution(clone);
        }
    }

    /** 目标评估明细 */
    public record Evaluation(double objective, int minStaffViolationCells, int consecutiveViolations,
                            int maxConsecutive, double restDaysStd, int[] restDays,
                            double shiftBalanceStdSum, int[][] perDayPerShift, int[][] perEmpShift) {
    }

    /** 违规项（失败降级时随贪心解一并返回，站长可见「哪天/哪班缺人」） */
    public record Violation(String rule, int dayIndex, Integer shiftIndex, Integer employeeId,
                            String detail) {
    }

    /** 规划结果 */
    public record Plan(Solution solution, Evaluation evaluation, List<Violation> violations, boolean fallback) {
    }

    // ==================== 对外入口 ====================

    /** 无既存排班约束的规划 */
    public static Plan plan(Config cfg, int employees, int days, int shifts) {
        return plan(cfg, employees, days, shifts, null, DEFAULT_SEED);
    }

    /**
     * 规划（贪心构造 → 若 {@code fixed} 为空亦照常跑退火）。
     *
     * @param fixed      既存排班约束矩阵：{@code null} 表示自由；{@code -1} 表示固定轮休；{@code ≥0} 表示固定班次。
     *                   用于 {@code skipExisting=true} 时「已存在排班不被重排」。为 null 时行为与原型逐位一致。
     * @param seed       退火随机种子（固定种子可复现）
     */
    public static Plan plan(Config cfg, int employees, int days, int shifts, Integer[][] fixed, long seed) {
        // 空输入边界：无员工/无天数/无班次时直接返回空解，避免退火阶段访问越界
        if (employees <= 0 || days <= 0 || shifts <= 0) {
            Solution empty = new Solution(new int[Math.max(0, employees)][Math.max(0, days)]);
            return new Plan(empty, evaluate(empty, cfg, Math.max(1, shifts)), List.of(), false);
        }
        Solution greedy = greedy(cfg, employees, days, shifts, fixed);
        try {
            Solution annealed = simulatedAnnealing(greedy, cfg, employees, days, shifts, fixed, seed);
            // 退火结果不劣于贪心才采用（罚函数单调，正常情况下成立；异常降温参数下防御性兜底）
            Evaluation greedyEval = evaluate(greedy, cfg, shifts);
            Evaluation annealedEval = evaluate(annealed, cfg, shifts);
            if (annealedEval.objective() <= greedyEval.objective()) {
                return new Plan(annealed, annealedEval, collectViolations(annealed, cfg, shifts), false);
            }
            return new Plan(greedy, greedyEval, collectViolations(greedy, cfg, shifts), true);
        } catch (RuntimeException e) {
            // 失败降级：任何算法异常都回落贪心解 + 违规清单，绝不失败（架构 §1.3）
            Evaluation greedyEval = evaluate(greedy, cfg, shifts);
            return new Plan(greedy, greedyEval, collectViolations(greedy, cfg, shifts), true);
        }
    }

    // ==================== 贪心构造 ====================

    /**
     * Phase1 轮休铺排：{@code (day+emp)%restCycle == restCycle-1} → 每人每周期休 1 天，轮休与班次解耦；
     * Phase2 班次分配：逐日「最短缺优先」，并列时给该员工历史最少的班次。
     */
    public static Solution greedy(Config cfg, int employees, int days, int shifts, Integer[][] fixed) {
        int[][] shift = new int[employees][days];
        for (int d = 0; d < days; d++) {
            List<Integer> onDuty = new ArrayList<>();
            int[] count = new int[shifts];
            for (int e = 0; e < employees; e++) {
                Integer fixedValue = (fixed == null) ? null : fixed[e][d];
                if (fixedValue != null) {
                    shift[e][d] = fixedValue;
                    if (fixedValue >= 0) {
                        count[fixedValue]++;
                    }
                    continue;
                }
                shift[e][d] = -1;
                if (cfg.restCycleDays() > 0 && (d + e) % cfg.restCycleDays() == cfg.restCycleDays() - 1) {
                    continue; // 轮休
                }
                onDuty.add(e);
            }
            // 每个在岗员工的「历史班次计数」（只统计本日之前的天，避免与本日 count 互相污染）
            int[][] empShiftCount = new int[onDuty.size()][shifts];
            for (int idx = 0; idx < onDuty.size(); idx++) {
                int e = onDuty.get(idx);
                for (int k = 0; k < d; k++) {
                    if (shift[e][k] >= 0) {
                        empShiftCount[idx][shift[e][k]]++;
                    }
                }
            }
            for (int idx = 0; idx < onDuty.size(); idx++) {
                int e = onDuty.get(idx);
                int best = 0;
                double bestScore = Double.NEGATIVE_INFINITY;
                for (int s = 0; s < shifts; s++) {
                    double deficit = (cfg.minPerShift() - count[s]) * (double) GREEDY_DEFICIT_SCALE
                            - empShiftCount[idx][s];
                    if (deficit > bestScore) {
                        bestScore = deficit;
                        best = s;
                    }
                }
                shift[e][d] = best;
                count[best]++;
            }
        }
        return new Solution(shift);
    }

    // ==================== 目标评估 ====================

    /** 单遍统计（O(D·E)），返回 J 与各约束明细。shifts 为班次数量（含无人值守的班次，需完整统计缺口） */
    public static Evaluation evaluate(Solution sol, Config cfg, int shifts) {
        int employees = sol.shift().length;
        int days = employees == 0 ? 0 : sol.shift()[0].length;
        int shiftCount = Math.max(1, shifts);
        int[][] perDayPerShift = new int[days][shiftCount];
        int[][] perEmpShift = new int[employees][shiftCount];
        int[] restDays = new int[employees];
        int maxRun = 0;
        int consecutiveViolations = 0;

        for (int e = 0; e < employees; e++) {
            int run = 0;
            for (int d = 0; d < days; d++) {
                int s = sol.shift()[e][d];
                if (s >= 0 && s < shiftCount) {
                    perDayPerShift[d][s]++;
                    perEmpShift[e][s]++;
                    run++;
                    maxRun = Math.max(maxRun, run);
                    if (run > cfg.maxConsecutiveWork()) {
                        consecutiveViolations++;
                    }
                } else {
                    restDays[e]++;
                    run = 0;
                }
            }
        }

        int minStaffViolationCells = 0;
        int coverageDeficit = 0;
        for (int d = 0; d < days; d++) {
            for (int s = 0; s < shiftCount; s++) {
                if (perDayPerShift[d][s] < cfg.minPerShift()) {
                    minStaffViolationCells++;
                    coverageDeficit += cfg.minPerShift() - perDayPerShift[d][s];
                }
            }
        }

        double shiftBalance = 0;
        for (int e = 0; e < employees; e++) {
            double sd = std(perEmpShift[e]);
            shiftBalance += sd * sd;
        }
        double restSpread = std(restDays);
        double objective = cfg.wMinStaff() * minStaffViolationCells
                + cfg.wConsecutive() * consecutiveViolations
                + cfg.wCoverageDeficit() * coverageDeficit
                + cfg.wShiftBalance() * shiftBalance
                + cfg.wRestSpread() * restSpread;

        return new Evaluation(round3(objective), minStaffViolationCells, consecutiveViolations, maxRun,
                round3(restSpread), restDays, round3(shiftBalance), perDayPerShift, perEmpShift);
    }

    // ==================== 模拟退火 ====================

    /**
     * 模拟退火局部搜索（同日班次互换 / 在岗↔轮休对调）。
     * 终止条件为固定迭代数（禁用「时间到即停」，否则破坏可复现性，架构 §5.2）。
     */
    public static Solution simulatedAnnealing(Solution start, Config cfg, int employees, int days, int shifts,
                                              Integer[][] fixed, long seed) {
        Mulberry32 rnd = new Mulberry32(seed);
        Solution cur = start.copy();
        Evaluation curEval = evaluate(cur, cfg, shifts);
        Solution best = cur.copy();
        Evaluation bestEval = curEval;
        double temp = cfg.saInitialTemp();

        for (int it = 0; it < cfg.saIterations(); it++) {
            Solution cand = cur.copy();
            if (rnd.nextDouble() < 0.5) {
                // 移动 1：同日两员工互换班次（只动班次，不动轮休）
                int d = rnd.nextInt(0, days - 1);
                List<Integer> working = new ArrayList<>();
                for (int e = 0; e < employees; e++) {
                    if (cand.shift()[e][d] >= 0) {
                        working.add(e);
                    }
                }
                if (working.size() >= 2) {
                    int i = rnd.nextInt(0, working.size() - 1);
                    int j = rnd.nextInt(0, working.size() - 1);
                    if (i == j) {
                        j = (j + 1) % working.size();
                    }
                    int a = working.get(i);
                    int b = working.get(j);
                    // fixed 格不可动；把 a、b 置同值即等价「空操作」（RNG 序列保持不变，可复现）
                    if (isFixed(fixed, a, d) || isFixed(fixed, b, d)) {
                        b = a;
                    }
                    int tmp = cand.shift()[a][d];
                    cand.shift()[a][d] = cand.shift()[b][d];
                    cand.shift()[b][d] = tmp;
                }
            } else {
                // 移动 2：同日「在岗 ↔ 轮休」对调（改变轮休分布，用于修最少在岗缺口）
                int d = rnd.nextInt(0, days - 1);
                List<Integer> resting = new ArrayList<>();
                List<Integer> working = new ArrayList<>();
                for (int e = 0; e < employees; e++) {
                    if (cand.shift()[e][d] >= 0) {
                        working.add(e);
                    } else {
                        resting.add(e);
                    }
                }
                if (!resting.isEmpty() && !working.isEmpty()) {
                    int r = resting.get(rnd.nextInt(0, resting.size() - 1));
                    int w = working.get(rnd.nextInt(0, working.size() - 1));
                    if (!isFixed(fixed, r, d) && !isFixed(fixed, w, d)) {
                        cand.shift()[r][d] = cand.shift()[w][d];
                        cand.shift()[w][d] = -1;
                    }
                }
            }

            Evaluation candEval = evaluate(cand, cfg, shifts);
            double delta = candEval.objective() - curEval.objective();
            if (delta <= 0 || rnd.nextDouble() < Math.exp(-delta / temp)) {
                cur = cand;
                curEval = candEval;
                if (candEval.objective() < bestEval.objective()) {
                    best = cand.copy();
                    bestEval = candEval;
                }
            }
            temp *= cfg.saCooling();
        }
        return best;
    }

    // ==================== 违规清单 ====================

    /** 汇总违规：每日每班最少在岗缺口 + 单员工连续工作超限。shifts 为完整班次数（含无人值守班次） */
    public static List<Violation> collectViolations(Solution sol, Config cfg, int shifts) {
        List<Violation> violations = new ArrayList<>();
        int days = sol.shift().length == 0 ? 0 : sol.shift()[0].length;
        int shiftCount = Math.max(1, shifts);
        int[][] perDayPerShift = new int[days][shiftCount];
        for (int e = 0; e < sol.shift().length; e++) {
            for (int d = 0; d < days; d++) {
                int s = sol.shift()[e][d];
                if (s >= 0 && s < shiftCount) {
                    perDayPerShift[d][s]++;
                }
            }
        }
        for (int d = 0; d < days; d++) {
            for (int s = 0; s < shiftCount; s++) {
                int actual = perDayPerShift[d][s];
                if (actual < cfg.minPerShift()) {
                    violations.add(new Violation("MIN_STAFF", d, s, null,
                            "第 " + (d + 1) + " 天第 " + (s + 1) + " 班在岗 " + actual + " 人，少于 "
                                    + cfg.minPerShift() + " 人"));
                }
            }
        }
        int employees = sol.shift().length;
        for (int e = 0; e < employees; e++) {
            int run = 0;
            int runStart = -1;
            for (int d = 0; d < sol.shift()[e].length; d++) {
                if (sol.shift()[e][d] >= 0) {
                    if (run == 0) {
                        runStart = d;
                    }
                    run++;
                } else {
                    if (run > cfg.maxConsecutiveWork()) {
                        violations.add(new Violation("CONSECUTIVE", runStart, null, e,
                                "第 " + (runStart + 1) + "-" + d + " 天连续工作 " + run + " 天，超过 "
                                        + cfg.maxConsecutiveWork() + " 天"));
                    }
                    run = 0;
                }
            }
            if (run > cfg.maxConsecutiveWork()) {
                violations.add(new Violation("CONSECUTIVE", runStart, null, e,
                        "第 " + (runStart + 1) + "-" + sol.shift()[e].length + " 天连续工作 " + run
                                + " 天，超过 " + cfg.maxConsecutiveWork() + " 天"));
            }
        }
        return violations;
    }

    // ==================== 工具 ====================

    private static boolean isFixed(Integer[][] fixed, int employee, int day) {
        return fixed != null && fixed[employee][day] != null;
    }

    /** 样本标准差（n-1 分母）；n&lt;2 返回 0（与原型 {@code lib/stats.mjs#std} 一致） */
    static double std(int[] values) {
        if (values.length < 2) {
            return 0;
        }
        double mean = 0;
        for (int v : values) {
            mean += v;
        }
        mean /= values.length;
        double acc = 0;
        for (int v : values) {
            acc += (v - mean) * (v - mean);
        }
        return Math.sqrt(acc / (values.length - 1));
    }

    private static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    /**
     * mulberry32（与 Mock {@code createRandom} / 原型 {@code lib/rng.mjs} 逐位等价）。
     * 用 int 溢出模拟 32 位无符号运算，保证「同种子同序列」。
     */
    static final class Mulberry32 {

        private int a;

        Mulberry32(long seed) {
            this.a = (int) (seed & 0xFFFFFFFFL);
        }

        double nextDouble() {
            a = a + 0x6d2b79f5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t = t ^ (t + ((t ^ (t >>> 7)) * (t | 61)));
            return (((long) (t ^ (t >>> 14))) & 0xFFFFFFFFL) / 4294967296d;
        }

        int nextInt(int min, int max) {
            return min + (int) Math.floor(nextDouble() * (max - min + 1));
        }
    }
}
