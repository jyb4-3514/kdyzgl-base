package com.qiujie.service.support;

import com.qiujie.config.AlgoProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LogFingerprintAggregator} 单元测试（S8 指纹去重 + 时间窗口聚合）。
 * <p>
 * 覆盖 S8 §10.4 单测边界：⑥ 全同指纹 ⑦ 全不同指纹 ⑧ 时间乱序 ⑨ 跨窗口边界（= 窗口长度）
 * ⑬ 时钟回拨，以及惰性清扫与容量兜底。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段跑</b>。
 */
class LogFingerprintAggregatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 24, 10, 0, 0);

    /** 构造指定窗口/清扫间隔/容量的聚合器（通过 AlgoProperties 注入，不依赖 Spring 容器） */
    private static LogFingerprintAggregator aggregator(int windowSeconds, int sweepEvery, int capacity) {
        AlgoProperties properties = new AlgoProperties();
        properties.getLog().setDedupeWindowSeconds(windowSeconds);
        properties.getLog().setSweepEvery(sweepEvery);
        properties.getLog().setRingBufferCap(capacity);
        return new LogFingerprintAggregator(properties);
    }

    @Test
    void sameFingerprintWithinWindowReturnsSameRowId() {
        LogFingerprintAggregator aggregator = aggregator(10, 2000, 200);
        aggregator.remember("fp", 7L, T0);

        assertEquals(7L, aggregator.findHit("fp", T0.plusSeconds(5)));
        assertEquals(7L, aggregator.findHit("fp", T0.plusSeconds(9)));
    }

    @Test
    void boundaryExactlyWindowIsHitBeyondIsMiss() {
        LogFingerprintAggregator aggregator = aggregator(10, 2000, 200);
        aggregator.remember("fp", 7L, T0);

        // 先验越窗（未命中不推进锚点，故后续边界断言仍以 T0 为基准）
        assertNull(aggregator.findHit("fp", T0.plusSeconds(11)), "差值 > 窗口视为未命中");
        assertEquals(7L, aggregator.findHit("fp", T0.plusSeconds(10)), "差值 = 窗口长度视为命中（<=）");
    }

    @Test
    void differentFingerprintsAreIndependent() {
        LogFingerprintAggregator aggregator = aggregator(10, 2000, 200);
        aggregator.remember("a", 1L, T0);
        aggregator.remember("b", 2L, T0);

        assertEquals(1L, aggregator.findHit("a", T0.plusSeconds(1)));
        assertEquals(2L, aggregator.findHit("b", T0.plusSeconds(1)));
        assertNull(aggregator.findHit("c", T0.plusSeconds(1)));
    }

    @Test
    void outOfOrderAndClockRollbackCountsAsHit() {
        LogFingerprintAggregator aggregator = aggregator(10, 2000, 200);
        aggregator.remember("fp", 7L, T0.plusSeconds(20));

        // 时钟回拨：事件时间早于 lastTime，差值 = -20 <= 窗口 → 命中，且锚点回退
        assertEquals(7L, aggregator.findHit("fp", T0));
        assertEquals(7L, aggregator.findHit("fp", T0.plusSeconds(3)));
    }

    @Test
    void lazySweepEvictsExpiredEntries() {
        LogFingerprintAggregator aggregator = aggregator(10, 2, 200);
        aggregator.remember("fp", 7L, T0);

        aggregator.findHit("other", T0.plusSeconds(100)); // ops=1，不触发清扫
        assertEquals(1, aggregator.size());
        aggregator.findHit("other", T0.plusSeconds(100)); // ops=2，触发清扫
        assertEquals(0, aggregator.size(), "过期条目应在惰性清扫时被移除");
    }

    @Test
    void capacityEvictsLeastRecentlySeen() {
        LogFingerprintAggregator aggregator = aggregator(10, 1000, 2);
        aggregator.remember("a", 1L, T0);
        aggregator.remember("b", 2L, T0.plusSeconds(1));
        aggregator.remember("c", 3L, T0.plusSeconds(2));

        assertEquals(2, aggregator.size(), "容量上限生效");
        assertNull(aggregator.findHit("a", T0.plusSeconds(2)), "最近事件时间最早的条目被淘汰");
        assertEquals(2L, aggregator.findHit("b", T0.plusSeconds(3)));
        assertEquals(3L, aggregator.findHit("c", T0.plusSeconds(3)));
    }

    @Test
    void evictAndClear() {
        LogFingerprintAggregator aggregator = aggregator(10, 2000, 200);
        aggregator.remember("fp", 7L, T0);
        aggregator.evict("fp");
        assertNull(aggregator.findHit("fp", T0.plusSeconds(1)));

        aggregator.remember("fp", 8L, T0);
        aggregator.clear();
        assertEquals(0, aggregator.size());
        assertNull(aggregator.findHit("fp", T0.plusSeconds(1)));
    }
}
