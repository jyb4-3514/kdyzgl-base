package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 排班算法单测（边界清单对齐 algo-hrm-server.md §5.4）：
 * ① 人力不足 ② 单人驿站 ③ minPerShift=0 ④ T=1 ⑤ maxConsecutiveWork=1 ⑥ restCycleDays=1
 * ⑦ 唯一性（同格不重复） ⑧ 已存在排班（fixed）交互 ⑨ 同种子结果一致 ⑩ 与原型指标一致（覆盖率 1.0 / 违规 0）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行；
 * 指标对照见交付摘要「Java 实现 ↔ Node 原型」一节。</p>
 */
class SchedulePlannerTest {

    /** 与原型 s3-schedule.mjs 默认配置逐键一致 */
    private SchedulePlanner.Config defaultConfig() {
        return new SchedulePlanner.Config(2, 5, 6, 1000, 1000, 10, 1, 0.1, 6000, 8, 0.9995);
    }

    @Test
    @DisplayName("贪心构造：8 人 × 30 天 × 3 班 → 覆盖率 1.0、最少在岗违规 0（对齐原型贪心列）")
    void greedyReachesFullCoverage() {
        SchedulePlanner.Config cfg = defaultConfig();
        SchedulePlanner.Solution solution = SchedulePlanner.greedy(cfg, 8, 30, 3, null);
        SchedulePlanner.Evaluation evaluation = SchedulePlanner.evaluate(solution, cfg, 3);
        assertEquals(0, evaluation.minStaffViolationCells());
        assertEquals(1.0, coverage(evaluation, cfg.minPerShift()), 1e-9);
    }

    @Test
    @DisplayName("贪心 + 模拟退火：目标函数不劣于贪心；最少在岗违规仍为 0（对齐原型退火列）")
    void annealingDoesNotWorsen() {
        SchedulePlanner.Config cfg = defaultConfig();
        SchedulePlanner.Solution greedy = SchedulePlanner.greedy(cfg, 8, 30, 3, null);
        double greedyJ = SchedulePlanner.evaluate(greedy, cfg, 3).objective();

        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, 8, 30, 3);
        assertTrue(plan.evaluation().objective() <= greedyJ + 1e-9,
                "退火 J=" + plan.evaluation().objective() + " 贪心 J=" + greedyJ);
        assertEquals(0, plan.evaluation().minStaffViolationCells());
        assertEquals(1.0, coverage(plan.evaluation(), cfg.minPerShift()), 1e-9);
        assertFalse(plan.fallback());
    }

    @Test
    @DisplayName("同种子可复现：两次规划结果逐位一致")
    void deterministicWithFixedSeed() {
        SchedulePlanner.Config cfg = defaultConfig();
        SchedulePlanner.Plan first = SchedulePlanner.plan(cfg, 8, 30, 3, null, SchedulePlanner.DEFAULT_SEED);
        SchedulePlanner.Plan second = SchedulePlanner.plan(cfg, 8, 30, 3, null, SchedulePlanner.DEFAULT_SEED);
        assertEquals(first.evaluation().objective(), second.evaluation().objective(), 1e-9);
        for (int e = 0; e < first.solution().shift().length; e++) {
            assertArrayEquals(first.solution().shift()[e], second.solution().shift()[e]);
        }
    }

    @Test
    @DisplayName("人力不足（minPerShift=50）：返回解 + 违规清单，不抛异常（失败降级）")
    void infeasibleReturnsPlanWithViolations() {
        SchedulePlanner.Config cfg = new SchedulePlanner.Config(50, 5, 6, 1000, 1000, 10, 1, 0.1, 600, 8, 0.9995);
        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, 4, 7, 3);
        assertNotNull(plan);
        assertFalse(plan.violations().isEmpty());
        assertTrue(plan.violations().stream().anyMatch(v -> "MIN_STAFF".equals(v.rule())));
    }

    @Test
    @DisplayName("单人驿站：可排、不抛异常（最少在岗不可达 → 违规清单非空）")
    void singleEmployee() {
        SchedulePlanner.Plan plan = SchedulePlanner.plan(defaultConfig(), 1, 10, 3);
        assertEquals(1, plan.solution().shift().length);
        assertFalse(plan.violations().isEmpty());
    }

    @Test
    @DisplayName("minPerShift=0：无最少在岗违规")
    void zeroMinPerShift() {
        SchedulePlanner.Config cfg = new SchedulePlanner.Config(0, 5, 6, 1000, 1000, 10, 1, 0.1, 600, 8, 0.9995);
        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, 4, 7, 3);
        assertEquals(0, plan.evaluation().minStaffViolationCells());
    }

    @Test
    @DisplayName("T=1：单天排班可解，不抛异常")
    void singleDay() {
        SchedulePlanner.Plan plan = SchedulePlanner.plan(defaultConfig(), 6, 1, 3);
        assertEquals(1, plan.evaluation().perDayPerShift().length);
    }

    @Test
    @DisplayName("maxConsecutiveWork=1：出现 CONSECUTIVE 违规项")
    void consecutiveLimitOne() {
        SchedulePlanner.Config cfg = new SchedulePlanner.Config(2, 1, 6, 1000, 1000, 10, 1, 0.1, 600, 8, 0.9995);
        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, 6, 10, 3);
        assertTrue(plan.violations().stream().anyMatch(v -> "CONSECUTIVE".equals(v.rule())));
    }

    @Test
    @DisplayName("restCycleDays=1：全员每天轮休（班次全 -1），违规清单非空")
    void restEveryDay() {
        SchedulePlanner.Config cfg = new SchedulePlanner.Config(2, 5, 1, 1000, 1000, 10, 1, 0.1, 100, 8, 0.9995);
        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, 4, 5, 3);
        for (int[] row : plan.solution().shift()) {
            for (int value : row) {
                assertEquals(-1, value);
            }
        }
        assertEquals(5 * 3, plan.evaluation().minStaffViolationCells());
    }

    @Test
    @DisplayName("已存在排班（fixed）：固定格在退火前后均不被改动")
    void fixedCellsAreRespected() {
        SchedulePlanner.Config cfg = defaultConfig();
        Integer[][] fixed = new Integer[4][3];
        fixed[0][0] = 1;
        fixed[1][0] = -1;
        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, 4, 3, 3, fixed, SchedulePlanner.DEFAULT_SEED);
        assertEquals(1, plan.solution().shift()[0][0]);
        assertEquals(-1, plan.solution().shift()[1][0]);
    }

    @Test
    @DisplayName("空输入（0 员工 / 0 天）：返回空解而不抛异常")
    void emptyInput() {
        SchedulePlanner.Plan plan = SchedulePlanner.plan(defaultConfig(), 0, 0, 3);
        assertEquals(0, plan.solution().shift().length);
    }

    /** 覆盖率 = 达标格数 / 总格数（对齐原型 coverageStats） */
    private double coverage(SchedulePlanner.Evaluation evaluation, int minPerShift) {
        int[][] grid = evaluation.perDayPerShift();
        if (grid.length == 0 || grid[0].length == 0) {
            return 1.0;
        }
        int cells = 0;
        int satisfied = 0;
        for (int[] row : grid) {
            for (int value : row) {
                cells++;
                if (value >= minPerShift) {
                    satisfied++;
                }
            }
        }
        return (double) satisfied / cells;
    }
}
