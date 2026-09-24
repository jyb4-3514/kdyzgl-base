package com.qiujie.service.support.geo.impl;

import com.qiujie.service.attendance.support.HaversineCalculator;
import com.qiujie.service.support.geo.GeoAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Haversine 地理端口实现单测（M2）：与既有 {@link HaversineCalculator} <b>行为等价</b>、围栏边界、降级语义。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HaversineGeoServiceTest {

    private static final double LNG = 117.201;
    private static final double LAT = 31.821;
    private final HaversineGeoService geo = new HaversineGeoService();

    @Test
    @DisplayName("距离与 HaversineCalculator 逐位等价（端口化不改计算结果）")
    void distanceEquivalentToCalculator() {
        assertEquals(HaversineCalculator.distance(LNG, LAT, LNG + 0.01, LAT + 0.01),
                geo.distance(LNG, LAT, LNG + 0.01, LAT + 0.01), 1e-9);
        assertEquals(0.0, geo.distance(LNG, LAT, LNG, LAT), 1e-9);
        assertTrue(Double.isNaN(geo.distance(null, LAT, LNG, LAT)));
        assertTrue(Double.isNaN(geo.distance(LNG, Double.NaN, LNG, LAT)));
    }

    @Test
    @DisplayName("围栏：同点命中；超半径不命中；radius 为 null 不命中；坐标非法视为不命中")
    void inFenceBoundaries() {
        assertTrue(geo.inFence(LNG, LAT, LNG, LAT, 0));                       // 距离 0 <= 0
        assertTrue(geo.inFence(LNG, LAT, LNG, LAT, 200));
        assertFalse(geo.inFence(LNG, LAT, LNG + 0.01, LAT, 300));             // 约 900~1000 米 > 300
        assertTrue(geo.inFence(LNG, LAT, LNG + 0.01, LAT, 1000));
        assertFalse(geo.inFence(LNG, LAT, LNG, LAT, null));                   // 无半径 → 不命中
        assertFalse(geo.inFence(null, LAT, LNG, LAT, 200));                   // 不可计算 → 不命中
    }

    @Test
    @DisplayName("降级语义：regeo 返回 null（不做逆地理编码）；provider=haversine")
    void degradedSemantics() {
        GeoAddress address = geo.regeo(LNG, LAT);
        assertNull(address);
        assertEquals(HaversineGeoService.PROVIDER, geo.provider());
    }
}
