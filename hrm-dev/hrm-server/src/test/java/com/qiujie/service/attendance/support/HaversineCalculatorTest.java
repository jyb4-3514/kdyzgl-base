package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Haversine 距离单测（边界：同点=0、不可计算=NaN、单位与量级合理）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HaversineCalculatorTest {

    private static final double LONGITUDE = 117.201;
    private static final double LATITUDE = 31.821;

    @Test
    @DisplayName("同一坐标距离为 0（围栏边界 0 <= radius 恒命中）")
    void samePointIsZero() {
        assertEquals(0.0, HaversineCalculator.distance(LONGITUDE, LATITUDE, LONGITUDE, LATITUDE), 1e-9);
    }

    @Test
    @DisplayName("经度偏移 0.01° 约 900~1000 米（球面量级校验）")
    void longitudeOffsetScale() {
        double distance = HaversineCalculator.distance(LONGITUDE, LATITUDE, LONGITUDE + 0.01, LATITUDE);
        assertTrue(distance > 900 && distance < 1000, "实际距离=" + distance);
    }

    @Test
    @DisplayName("入参含 null / 非有限值 → NaN（对齐 Mock 的 distance 置 null）")
    void invalidInputReturnsNaN() {
        assertTrue(Double.isNaN(HaversineCalculator.distance(null, LATITUDE, LONGITUDE, LATITUDE)));
        assertTrue(Double.isNaN(HaversineCalculator.distance(LONGITUDE, Double.NaN, LONGITUDE, LATITUDE)));
    }

    @Test
    @DisplayName("距离对称：A→B 与 B→A 一致")
    void symmetric() {
        double ab = HaversineCalculator.distance(LONGITUDE, LATITUDE, LONGITUDE + 0.01, LATITUDE + 0.01);
        double ba = HaversineCalculator.distance(LONGITUDE + 0.01, LATITUDE + 0.01, LONGITUDE, LATITUDE);
        assertEquals(ab, ba, 1e-6);
    }
}
