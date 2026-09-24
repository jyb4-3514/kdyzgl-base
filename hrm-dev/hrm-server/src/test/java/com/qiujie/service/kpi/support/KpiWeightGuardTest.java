package com.qiujie.service.kpi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权重守卫单测（边界：空集 / 99 / 100 / 101 / 容差）。
 * <p>与 Mock {@code weightError} 及原型「权重守卫」实测一致：30/20/15/15/10/10 通过、…/15（105）拒绝、
 * 无启用指标不校验。注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class KpiWeightGuardTest {

    @Test
    @DisplayName("启用合计 = 100 通过")
    void pass() {
        KpiWeightGuard.Result r = KpiWeightGuard.check(List.of(30, 20, 15, 15, 10, 10), 100, 0);
        assertTrue(r.pass());
        assertEquals(100, r.enabledSum());
        assertNull(r.message());
    }

    @Test
    @DisplayName("合计 99 / 101 拒绝，文案含当前合计")
    void fail() {
        KpiWeightGuard.Result low = KpiWeightGuard.check(List.of(30, 20, 15, 15, 10, 9), 100, 0);
        assertFalse(low.pass());
        assertEquals(99, low.enabledSum());
        assertTrue(low.message().contains("99"));

        KpiWeightGuard.Result high = KpiWeightGuard.check(List.of(30, 20, 15, 15, 10, 15), 100, 0);
        assertFalse(high.pass());
        assertTrue(high.message().contains("105"));
    }

    @Test
    @DisplayName("无启用指标不校验（放行，交由算分报 NO_METRIC）")
    void emptyPasses() {
        KpiWeightGuard.Result r = KpiWeightGuard.check(List.of(), 100, 0);
        assertTrue(r.pass());
        assertEquals(0, r.enabledSum());
    }

    @Test
    @DisplayName("容差内放行（合计 105、容差 5）")
    void tolerance() {
        assertTrue(KpiWeightGuard.check(List.of(105), 100, 5).pass());
        assertFalse(KpiWeightGuard.check(List.of(106), 100, 5).pass());
    }
}
