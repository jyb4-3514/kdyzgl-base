package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打卡判定链单测（边界：时间窗闭区间两端、单班次 vs 时段两套窗口、ALL/ANY、围栏边界、迟到/早退阈值分钟）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceCheckPolicyTest {

    private static final int ON_START = 480;   // 08:00
    private static final int ON_END = 720;     // 12:00
    private static final Double FENCE_LNG = 117.201;
    private static final Double FENCE_LAT = 31.821;

    // ==================== 时间窗 ====================

    @Test
    @DisplayName("时段模型窗口 [start−early, end+late] 为闭区间：两端在窗内，越界 1 分钟回 9102")
    void periodWindowBoundaries() {
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "ON", 450)).code());
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "ON", 780)).code());
        assertEquals(9102, AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "ON", 449)).code());
        assertEquals(9102, AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "ON", 781)).code());
    }

    @Test
    @DisplayName("单班次模型：上班卡 [start−30, end]，end 之后即越窗")
    void singleShiftOnWindow() {
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "ON", 450)).code());
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "ON", 720)).code());
        assertEquals(9102, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "ON", 721)).code());
    }

    @Test
    @DisplayName("单班次模型：下班卡 [start, end+60]，start 之前即越窗")
    void singleShiftOffWindow() {
        assertEquals(9102, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "OFF", 479)).code());
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "OFF", 480)).code());
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "OFF", 780)).code());
        assertEquals(9102, AttendanceCheckPolicy.evaluate(facts(false, ON_START, ON_END, "OFF", 781)).code());
    }

    @Test
    @DisplayName("windowCode 单点暴露：越窗 9102 / 在窗 200（Service 据此在「去重」前短路，保证判定链顺序）")
    void windowCodeExposed() {
        assertEquals(9102, AttendanceCheckPolicy.windowCode(facts(true, ON_START, ON_END, "ON", 449)));
        assertEquals(200, AttendanceCheckPolicy.windowCode(facts(true, ON_START, ON_END, "ON", 450)));
    }

    @Test
    @DisplayName("时间窗关闭时不判越窗（仅余校验项与迟到早退）")
    void timeWindowDisabled() {
        AttendanceCheckPolicy.ClockFacts facts = new AttendanceCheckPolicy.ClockFacts(
                false, true, ON_START, ON_END, 30, 60, "ON", 0,
                false, false, "ALL", List.of(), null, null, null, null, null, null, 30, 30);
        assertEquals(200, AttendanceCheckPolicy.evaluate(facts).code());
    }

    // ==================== 校验项 ====================

    @Test
    @DisplayName("无启用校验项时直接通过，checkMode 回落 WIFI+LOCATION")
    void noVerifyEnabled() {
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(
                facts(true, ON_START, ON_END, "ON", 480));
        assertTrue(decision.passed());
        assertEquals(AttendanceConstants.CHECK_MODE_BOTH, decision.checkMode());
    }

    @Test
    @DisplayName("ALL 模式 WiFi 命中 → 通过，checkMode=WIFI")
    void wifiMatch() {
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(
                wifiFacts("ALL", List.of("ST001-Express"), "ST001-Express", false));
        assertTrue(decision.passed());
        assertEquals(AttendanceConstants.CHECK_MODE_WIFI, decision.checkMode());
    }

    @Test
    @DisplayName("WiFi 未命中 → 9103 + ABNORMAL")
    void wifiMismatch() {
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(
                wifiFacts("ALL", List.of("ST001-Express"), "Other-WiFi", false));
        assertEquals(9103, decision.code());
        assertEquals(AttendanceConstants.STATUS_ABNORMAL, decision.status());
    }

    @Test
    @DisplayName("定位：同点距离 0、围栏半径 0 → 恰好相等按命中处理（<= 口径）")
    void locationBoundaryEqualRadius() {
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(
                locationFacts(0.0, 0.0, 0.0, 0.0, 0));
        assertTrue(decision.locationMatched());
        assertEquals(AttendanceConstants.CHECK_MODE_LOCATION, decision.checkMode());
    }

    @Test
    @DisplayName("定位：超出围栏半径 → 9104")
    void locationOutOfRadius() {
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(
                locationFacts(0.0, 0.0, 0.0, 0.01, 300));
        assertEquals(9104, decision.code());
        assertFalse(decision.locationMatched());
    }

    @Test
    @DisplayName("ANY 模式：WiFi 未命中但定位命中 → 通过，checkMode=LOCATION")
    void anyModeLocationHit() {
        AttendanceCheckPolicy.ClockFacts facts = new AttendanceCheckPolicy.ClockFacts(
                true, true, ON_START, ON_END, 30, 60, "ON", 480,
                true, true, "ANY", List.of("ST001-Express"), "Other-WiFi",
                FENCE_LNG, FENCE_LAT, FENCE_LNG, FENCE_LAT, 300, 30, 30);
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(facts);
        assertTrue(decision.passed());
        assertEquals(AttendanceConstants.CHECK_MODE_LOCATION, decision.checkMode());
    }

    @Test
    @DisplayName("ALL 模式：WiFi 先失败 → 按首个未通过项回 9103")
    void allModeFirstFailIsWifi() {
        AttendanceCheckPolicy.ClockFacts facts = new AttendanceCheckPolicy.ClockFacts(
                true, true, ON_START, ON_END, 30, 60, "ON", 480,
                true, true, "ALL", List.of("ST001-Express"), "Other-WiFi",
                0.0, 0.0, 0.0, 0.01, 300, 30, 30);
        assertEquals(9103, AttendanceCheckPolicy.evaluate(facts).code());
    }

    // ==================== 迟到 / 早退 ====================

    @Test
    @DisplayName("迟到阈值边界：now == start+阈值 → 正常；+1 分钟 → 迟到")
    void lateBoundary() {
        assertEquals(AttendanceConstants.STATUS_NORMAL,
                AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "ON", ON_START + 30)).status());
        assertEquals(AttendanceConstants.STATUS_LATE,
                AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "ON", ON_START + 31)).status());
    }

    @Test
    @DisplayName("早退阈值边界：now == end−阈值 → 正常；−1 分钟 → 早退")
    void earlyLeaveBoundary() {
        assertEquals(AttendanceConstants.STATUS_NORMAL,
                AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "OFF", ON_END - 30)).status());
        assertEquals(AttendanceConstants.STATUS_EARLY_LEAVE,
                AttendanceCheckPolicy.evaluate(facts(true, ON_START, ON_END, "OFF", ON_END - 31)).status());
    }

    // ==================== 构造工具 ====================

    /** 默认：时间窗开启、WiFi/定位均关闭、ALL、阈值 30、时段余量 30/60 */
    private AttendanceCheckPolicy.ClockFacts facts(boolean usePeriod, double startMin, double endMin,
                                                   String checkType, int nowMinutes) {
        return new AttendanceCheckPolicy.ClockFacts(true, usePeriod, startMin, endMin, 30, 60, checkType,
                nowMinutes, false, false, "ALL", List.of(), null, null, null, null, null, null, 30, 30);
    }

    private AttendanceCheckPolicy.ClockFacts wifiFacts(String matchMode, List<String> ssids, String reported,
                                                       boolean enableLocation) {
        return new AttendanceCheckPolicy.ClockFacts(true, true, ON_START, ON_END, 30, 60, "ON", 480,
                true, enableLocation, matchMode, ssids, reported, null, null, null, null, null, 30, 30);
    }

    private AttendanceCheckPolicy.ClockFacts locationFacts(Double fenceLng, Double fenceLat,
                                                            Double reportedLng, Double reportedLat, Integer radius) {
        return new AttendanceCheckPolicy.ClockFacts(true, true, ON_START, ON_END, 30, 60, "ON", 480,
                false, true, "ALL", List.of(), null, fenceLng, fenceLat, reportedLng, reportedLat, radius, 30, 30);
    }
}
