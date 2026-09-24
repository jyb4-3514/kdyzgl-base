package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4 异常检测单测（边界清单对齐 algo-hrm-server.md §6.4）：
 * ① 样本量 &lt; minSamples ② MAD=0（全同值） ③ 全员零迟到 ④ 单员工 ⑤ 连缺阈值边界 ⑥ 稳健 z 优于普通 z。
 * <p>口径说明：{@code minSamples} 为<b>场景级</b>下限（不足 → 整体空集）；
 * {@code MAD=0} 只降级「迟到频次」检测器（无离群可判），连续缺卡为阈值型检测器，不受 MAD 影响。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceAnomalyDetectorTest {

    private AttendanceAnomalyDetector.Config defaultConfig() {
        return new AttendanceAnomalyDetector.Config(true, 3.5, 5.0, 3, 5);
    }

    @Test
    @DisplayName("样本量 < minSamples（3 < 5）→ 整体空集（降级不告警）")
    void sampleTooSmall() {
        List<AttendanceAnomalyDetector.EmployeeData> data = List.of(
                data(1L, 1, 0), data(2L, 2, 0), data(3L, 9, 0));
        AttendanceAnomalyDetector.Report report = AttendanceAnomalyDetector.detect(data, defaultConfig());
        assertTrue(report.anomalies().isEmpty());
        assertEquals(3, report.sampleSize());
    }

    @Test
    @DisplayName("MAD=0（全同值）→ 不产生迟到频次异常；阈值型连缺仍生效")
    void madZeroDegradesLateOnly() {
        List<AttendanceAnomalyDetector.EmployeeData> data = List.of(
                data(1L, 4, 0), data(2L, 4, 0), data(3L, 4, 0),
                data(4L, 4, 3), data(5L, 4, 0), data(6L, 4, 0));
        AttendanceAnomalyDetector.Report report = AttendanceAnomalyDetector.detect(data, defaultConfig());
        assertFalse(report.anomalies().stream().anyMatch(a -> "LATE_FREQUENT".equals(a.type())));
        assertTrue(report.anomalies().stream().anyMatch(
                a -> "ABSENT_RUN".equals(a.type()) && a.employeeId().equals(4L)));
    }

    @Test
    @DisplayName("稳健 z：右偏数据下命中极端离群（z≈12.0，CRITICAL）")
    void robustZHitsExtremeOutlier() {
        List<AttendanceAnomalyDetector.EmployeeData> data = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            data.add(data((long) i, i, 0));
        }
        data.add(data(10L, 50, 0));

        AttendanceAnomalyDetector.Report report = AttendanceAnomalyDetector.detect(data, defaultConfig());
        assertEquals(5.5, report.median(), 1e-9);
        assertEquals(2.5, report.mad(), 1e-9);
        List<AttendanceAnomalyDetector.Anomaly> lateAnomalies = report.anomalies().stream()
                .filter(a -> "LATE_FREQUENT".equals(a.type())).toList();
        assertEquals(1, lateAnomalies.size());
        assertEquals(10L, lateAnomalies.get(0).employeeId());
        assertEquals("CRITICAL", lateAnomalies.get(0).severity());
        assertEquals(12.005, lateAnomalies.get(0).zValue(), 0.1);
    }

    @Test
    @DisplayName("普通 z（mean/std）在同一数据上漏报（复核原型「稳健统计更优」结论）")
    void plainZMissesOutlier() {
        List<AttendanceAnomalyDetector.EmployeeData> data = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            data.add(data((long) i, i, 0));
        }
        data.add(data(10L, 50, 0));

        AttendanceAnomalyDetector.Config plain = new AttendanceAnomalyDetector.Config(false, 3.5, 5.0, 3, 5);
        AttendanceAnomalyDetector.Report report = AttendanceAnomalyDetector.detect(data, plain);
        assertTrue(report.anomalies().stream().noneMatch(a -> "LATE_FREQUENT".equals(a.type())));
    }

    @Test
    @DisplayName("连续缺卡阈值边界：run==阈值 命中、run==阈值-1 不命中")
    void absentRunThresholdBoundary() {
        List<AttendanceAnomalyDetector.EmployeeData> data = List.of(
                data(1L, 0, 3), data(2L, 0, 2), data(3L, 0, 0),
                data(4L, 0, 0), data(5L, 0, 0));
        AttendanceAnomalyDetector.Report report = AttendanceAnomalyDetector.detect(data, defaultConfig());
        assertTrue(report.anomalies().stream().anyMatch(a -> a.employeeId().equals(1L)));
        assertTrue(report.anomalies().stream().noneMatch(a -> a.employeeId().equals(2L)));
    }

    @Test
    @DisplayName("全员零迟到且无连缺 → 空集")
    void allClean() {
        List<AttendanceAnomalyDetector.EmployeeData> data = List.of(
                data(1L, 0, 0), data(2L, 0, 0), data(3L, 0, 0), data(4L, 0, 0), data(5L, 0, 0));
        assertTrue(AttendanceAnomalyDetector.detect(data, defaultConfig()).anomalies().isEmpty());
    }

    private AttendanceAnomalyDetector.EmployeeData data(Long employeeId, int lateCount, int maxAbsentRun) {
        return new AttendanceAnomalyDetector.EmployeeData(employeeId, lateCount, maxAbsentRun);
    }
}
