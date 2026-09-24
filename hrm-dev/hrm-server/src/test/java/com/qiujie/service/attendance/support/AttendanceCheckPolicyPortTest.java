package com.qiujie.service.attendance.support;

import com.qiujie.service.support.geo.GeoAddress;
import com.qiujie.service.support.geo.GeoService;
import com.qiujie.service.support.geo.impl.HaversineGeoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 考勤围栏改走地理端口的等价性与端口消费单测（M2）：
 * ① 端口路径（HaversineGeoService）与默认路径结果<b>逐位等价</b>；② 端口确实被消费（注入不同距离即改变判定）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceCheckPolicyPortTest {

    private static final int ON_START = 480;
    private static final int ON_END = 720;
    private static final double FENCE_LNG = 117.201;
    private static final double FENCE_LAT = 31.821;

    /** 围栏中心=FENCE，上报点=同点（距离 0），半径 300，定位校验开启 */
    private static AttendanceCheckPolicy.ClockFacts locationFacts(Double reportedLng, Double reportedLat, Integer radius) {
        return new AttendanceCheckPolicy.ClockFacts(
                true, true, ON_START, ON_END, 30, 60, "ON", 480,
                false, true, "ALL", List.of(), null,
                FENCE_LNG, FENCE_LAT, reportedLng, reportedLat, radius, 30, 30);
    }

    /** 构造返回固定距离的桩端口，用于证明端口被实际消费 */
    private static GeoService fixedDistance(double meters) {
        return new GeoService() {
            @Override
            public GeoAddress regeo(Double longitude, Double latitude) {
                return null;
            }

            @Override
            public double distance(Double lng1, Double lat1, Double lng2, Double lat2) {
                return meters;
            }

            @Override
            public boolean inFence(Double centerLng, Double centerLat, Double pointLng, Double pointLat, Integer radius) {
                return radius != null && meters <= radius;
            }

            @Override
            public String provider() {
                return "stub";
            }
        };
    }

    @Test
    @DisplayName("等价性：端口路径（HaversineGeoService）与默认路径 code/checkMode/distance/status 完全一致")
    void portPathEqualsDefaultPath() {
        GeoService geo = new HaversineGeoService();
        AttendanceCheckPolicy.ClockFacts[] cases = {
                locationFacts(FENCE_LNG, FENCE_LAT, 300),        // 命中
                locationFacts(FENCE_LNG + 0.01, FENCE_LAT, 300), // 超半径 → 9104
                locationFacts(FENCE_LNG + 0.01, FENCE_LAT, 1000),// 命中（大半径）
                locationFacts(null, null, 300)                   // 不可计算 → 9104
        };
        for (AttendanceCheckPolicy.ClockFacts facts : cases) {
            AttendanceCheckPolicy.ClockDecision byPort = AttendanceCheckPolicy.evaluate(facts, geo);
            AttendanceCheckPolicy.ClockDecision byDefault = AttendanceCheckPolicy.evaluate(facts);
            assertEquals(byDefault.code(), byPort.code());
            assertEquals(byDefault.checkMode(), byPort.checkMode());
            assertEquals(byDefault.locationMatched(), byPort.locationMatched());
            assertEquals(byDefault.distance(), byPort.distance());
            assertEquals(byDefault.status(), byPort.status());
        }
    }

    @Test
    @DisplayName("geoService 为 null → 回落本地 Haversine，与默认路径一致")
    void nullGeoServiceFallsBack() {
        AttendanceCheckPolicy.ClockFacts facts = locationFacts(FENCE_LNG, FENCE_LAT, 300);
        AttendanceCheckPolicy.ClockDecision withNull = AttendanceCheckPolicy.evaluate(facts, null);
        assertEquals(AttendanceCheckPolicy.evaluate(facts).code(), withNull.code());
        assertTrue(withNull.locationMatched());
    }

    @Test
    @DisplayName("端口确实被消费：注入端口距离即决定围栏判定（同点坐标下 5 米命中 / 5000 米 9104）")
    void portDistanceIsConsumed() {
        AttendanceCheckPolicy.ClockFacts facts = locationFacts(FENCE_LNG, FENCE_LAT, 300);
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts, fixedDistance(5d)).code());
        assertEquals(9104, AttendanceCheckPolicy.evaluate(facts, fixedDistance(5000d)).code());
    }
}
