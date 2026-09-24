package com.qiujie.service.kpi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确定性业绩数据源单测（与 Mock {@code kpiStore.actualValueOf} 的逐位等价）。
 * <p>
 * 期望值为本机 <b>实跑 Node 25/24</b> 的 {@code fnv1a + mulberry32 + actualValueOf}
 * 参考脚本所得（与 {@code hrm-demo/src/shared/mock/kpiStore.js} 同实现）：
 * PARCEL#1#2026-09=919、PICKUP_TIMELY#1#2026-09=89.1、COMPLAINT#1#2026-09=0、
 * ATTENDANCE#1#2026-09=92.4、SERVICE#1#2026-09=5、WORK_ORDER#2#2026-09=19。
 * 若 Java 值与上表有一处不符，即说明 PRNG/哈希实现与 Mock 不再逐位等价。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行；Node 参考值已实跑核对。</p>
 */
class KpiSimulatedDataTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("与 Node/Mock 逐位等价：六项参考值（整数分支精确；小数分支容差 0.05，防 toFixed 舍入边界误判）")
    void referenceValues() {
        assertEquals(d("919"), KpiSimulatedData.actualValue("PARCEL", 1L, "2026-09", d("1200"), "件", "UP"));
        assertEquals(d("0"), KpiSimulatedData.actualValue("COMPLAINT", 1L, "2026-09", d("0"), "件", "DOWN"));
        assertEquals(d("19"), KpiSimulatedData.actualValue("WORK_ORDER", 2L, "2026-09", d("20"), "件", "UP"));
        assertEquals(89.1d, KpiSimulatedData.actualValue("PICKUP_TIMELY", 1L, "2026-09", d("95"), "%", "UP").doubleValue(), 0.05d);
        assertEquals(92.4d, KpiSimulatedData.actualValue("ATTENDANCE", 1L, "2026-09", d("100"), "%", "UP").doubleValue(), 0.05d);
        assertEquals(5.0d, KpiSimulatedData.actualValue("SERVICE", 1L, "2026-09", d("4.8"), "分", "UP").doubleValue(), 0.001d);
    }

    @Test
    @DisplayName("确定性：同参数多次调用结果恒定（演示数据刷新不变）")
    void deterministic() {
        BigDecimal first = KpiSimulatedData.actualValue("PARCEL", 7L, "2026-09", d("1200"), "件", "UP");
        BigDecimal second = KpiSimulatedData.actualValue("PARCEL", 7L, "2026-09", d("1200"), "件", "UP");
        assertEquals(0, first.compareTo(second));
    }

    @Test
    @DisplayName("值域：DOWN 取 0-3；% 落在 (0,100]；分 落在 (0,5]")
    void ranges() {
        for (long id = 1; id <= 20; id++) {
            BigDecimal down = KpiSimulatedData.actualValue("COMPLAINT", id, "2026-09", d("0"), "件", "DOWN");
            assertTrue(down.compareTo(BigDecimal.ZERO) >= 0 && down.compareTo(d("3")) <= 0);

            BigDecimal percent = KpiSimulatedData.actualValue("ATTENDANCE", id, "2026-09", d("100"), "%", "UP");
            assertTrue(percent.compareTo(BigDecimal.ZERO) > 0 && percent.compareTo(d("100")) <= 0);

            BigDecimal score = KpiSimulatedData.actualValue("SERVICE", id, "2026-09", d("4.8"), "分", "UP");
            assertTrue(score.compareTo(BigDecimal.ZERO) > 0 && score.compareTo(d("5")) <= 0);
        }
    }

    @Test
    @DisplayName("哈希确定性：同键同值、异键异种子")
    void hashStability() {
        assertEquals(KpiSimulatedData.fnv1a("PARCEL#1#2026-09"), KpiSimulatedData.fnv1a("PARCEL#1#2026-09"));
        assertTrue(KpiSimulatedData.fnv1a("PARCEL#1#2026-09") != KpiSimulatedData.fnv1a("PARCEL#2#2026-09"));
    }
}
