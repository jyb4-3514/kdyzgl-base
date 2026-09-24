package com.qiujie.service.attendance.support;

import com.qiujie.service.support.geo.GeoService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 打卡判定链（纯逻辑，无副作用、输入全显式）。
 * <p>
 * 判定顺序<b>不得变更</b>（对齐 Mock {@code attendanceStore.checkIn}）：
 * 规则 → 时段/班次 → 时间窗 → 重复 → 校验项（WiFi/定位，{@code matchMode} ALL/ANY）→ 迟到/早退。
 * 其中「规则未配 / 时段不存在 / 班次停用 / 重复打卡」依赖数据库，由 Service 前置判定；
 * 本类只承担「时间窗 → 校验项 → 迟到/早退」这三段纯计算，便于离线单测覆盖边界分钟与围栏边界。
 * <p>
 * 时间窗口径（两套模型，与 Mock 一致）：
 * <ul>
 *   <li>时段模型（传 periodIndex）：{@code [start − allowEarlyMin, end + allowLateMin]}；</li>
 *   <li>单班次模型（不传 periodIndex）：上班卡 {@code [start − 30, end]}、下班卡 {@code [start, end + 60]}。</li>
 * </ul>
 * 边界值（恰好等于 from/to）按「在窗内」处理（闭区间）。
 */
public final class AttendanceCheckPolicy {

    private AttendanceCheckPolicy() {
    }

    /**
     * 判定入参（全部显式，避免读上下文造成不可测）。
     *
     * @param enableTimeWindow   是否启用时间窗校验
     * @param usePeriod          true=时段模型，false=单班次模型
     * @param startMin           时间基准开始（分钟）
     * @param endMin             时间基准结束（分钟，24:00 = 1440）
     * @param allowEarlyMin      时段模型提前量（分钟）
     * @param allowLateMin       时段模型延后量（分钟）
     * @param checkType          ON / OFF
     * @param nowMinutes         当前时刻的当日分钟数
     * @param enableWifi         是否启用 WiFi 校验
     * @param enableLocation     是否启用定位校验
     * @param matchMode          ALL / ANY
     * @param wifiSsids          白名单 SSID 集合（可空）
     * @param reportedWifiSsid   本次上报 SSID（可空）
     * @param fenceLongitude     围栏中心经度（可空）
     * @param fenceLatitude      围栏中心纬度（可空）
     * @param reportedLongitude  本次上报经度（可空）
     * @param reportedLatitude   本次上报纬度（可空）
     * @param radius             围栏半径（米，可空 → 视为不命中）
     * @param lateThresholdMin   迟到阈值（分钟）
     * @param earlyLeaveMin      早退阈值（分钟）
     */
    public record ClockFacts(
            boolean enableTimeWindow,
            boolean usePeriod,
            double startMin,
            double endMin,
            int allowEarlyMin,
            int allowLateMin,
            String checkType,
            int nowMinutes,
            boolean enableWifi,
            boolean enableLocation,
            String matchMode,
            List<String> wifiSsids,
            String reportedWifiSsid,
            Double fenceLongitude,
            Double fenceLatitude,
            Double reportedLongitude,
            Double reportedLatitude,
            Integer radius,
            int lateThresholdMin,
            int earlyLeaveMin) {
    }

    /**
     * 判定结果。
     *
     * @param code            200=通过；9102=不在时间窗；9103=WiFi 未通过；9104=定位未通过
     * @param checkMode       命中校验项（WIFI / LOCATION / WIFI+LOCATION）
     * @param wifiMatched     WiFi 是否命中
     * @param distance        距围栏中心距离（米，保留 1 位；不可计算为 null）
     * @param locationMatched 定位是否命中
     * @param status          NORMAL / LATE / EARLY_LEAVE / ABNORMAL（校验未通过时 ABNORMAL）
     * @param remark          备注（迟到/早退/异常说明）
     */
    public record ClockDecision(
            int code,
            String checkMode,
            boolean wifiMatched,
            Double distance,
            boolean locationMatched,
            String status,
            String remark) {

        public boolean passed() {
            return code == 200;
        }
    }

    /** 执行「时间窗 → 校验项 → 迟到/早退」判定（默认走本地 Haversine，行为与改造前逐位等价） */
    public static ClockDecision evaluate(ClockFacts f) {
        DistanceFn haversine = HaversineCalculator::distance;
        return evaluate(f, haversine);
    }

    /**
     * 执行判定并<b>通过地理端口</b>计算围栏距离（生产路径，M2 接入）。
     * <p>
     * 默认/降级实现（{@code HaversineGeoService}）与生产实现（{@code AmapGeoService}）的 {@code distance}
     * 均委托 {@link HaversineCalculator}，故本重载与 {@link #evaluate(ClockFacts)} <b>行为等价</b>，
     * 不改变任何考勤出参/错误码（9104 等）。
     *
     * @param geoService 地理端口；为 {@code null} 时回落本地 Haversine
     */
    public static ClockDecision evaluate(ClockFacts f, GeoService geoService) {
        DistanceFn distanceFn = geoService == null ? HaversineCalculator::distance : geoService::distance;
        return evaluate(f, distanceFn);
    }

    /** 判定内核：距离计算以 {@link DistanceFn} 注入，便于测试与端口替换 */
    private static ClockDecision evaluate(ClockFacts f, DistanceFn distanceFn) {
        // ---------- ① 时间窗 ----------
        if (windowCode(f) != 200) {
            return new ClockDecision(9102, null, false, null, false, null, null);
        }

        // ---------- ② 校验项（WiFi / 定位，ALL / ANY） ----------
        boolean wifiMatched = f.enableWifi()
                && f.wifiSsids() != null
                && f.reportedWifiSsid() != null
                && f.wifiSsids().contains(f.reportedWifiSsid());

        double rawDistance = distanceFn.distance(
                f.fenceLongitude(), f.fenceLatitude(), f.reportedLongitude(), f.reportedLatitude());
        Double distance = Double.isFinite(rawDistance) ? round1(rawDistance) : null;
        boolean locationMatched = f.enableLocation()
                && distance != null
                && f.radius() != null
                && distance <= f.radius();

        List<String> enabled = new ArrayList<>();
        if (f.enableWifi()) {
            enabled.add(AttendanceConstants.CHECK_MODE_WIFI);
        }
        if (f.enableLocation()) {
            enabled.add(AttendanceConstants.CHECK_MODE_LOCATION);
        }
        List<String> matched = new ArrayList<>();
        if (wifiMatched) {
            matched.add(AttendanceConstants.CHECK_MODE_WIFI);
        }
        if (locationMatched) {
            matched.add(AttendanceConstants.CHECK_MODE_LOCATION);
        }
        // checkMode 只允许契约三项：无命中项时优先回退「已启用项」，无启用项再回退 WIFI+LOCATION
        String checkMode = !matched.isEmpty()
                ? String.join("+", matched)
                : (!enabled.isEmpty() ? String.join("+", enabled) : AttendanceConstants.CHECK_MODE_BOTH);

        boolean pass;
        if (enabled.isEmpty()) {
            pass = true; // 免校验兜底
        } else if (AttendanceConstants.MATCH_MODE_ALL.equals(f.matchMode())) {
            pass = matched.containsAll(enabled);
        } else {
            pass = enabled.stream().anyMatch(matched::contains);
        }

        if (!pass) {
            String firstFail = enabled.stream().filter(k -> !matched.contains(k)).findFirst().orElse(null);
            int code = AttendanceConstants.CHECK_MODE_WIFI.equals(firstFail) ? 9103 : 9104;
            // 校验未通过的尝试仍落一条异常卡留痕（Service 负责入库），状态记为 ABNORMAL
            return new ClockDecision(code, checkMode, wifiMatched, distance, locationMatched,
                    AttendanceConstants.STATUS_ABNORMAL, "打卡校验未通过");
        }

        // ---------- ③ 迟到 / 早退 ----------
        String status = AttendanceConstants.STATUS_NORMAL;
        String remark = null;
        if (AttendanceConstants.CHECK_TYPE_ON.equals(f.checkType())
                && f.nowMinutes() > f.startMin() + f.lateThresholdMin()) {
            status = AttendanceConstants.STATUS_LATE;
            remark = "迟到超过 " + f.lateThresholdMin() + " 分钟";
        }
        if (AttendanceConstants.CHECK_TYPE_OFF.equals(f.checkType())
                && f.nowMinutes() < f.endMin() - f.earlyLeaveMin()) {
            status = AttendanceConstants.STATUS_EARLY_LEAVE;
            remark = "早退超过 " + f.earlyLeaveMin() + " 分钟";
        }
        return new ClockDecision(200, checkMode, wifiMatched, distance, locationMatched, status, remark);
    }

    /** 距离保留 1 位小数（对齐 Mock {@code rawDistance.toFixed(1)}） */
    public static Double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 时间窗判定（单独暴露，供 Service 在「去重」之前先短路）：
     * 判定链顺序为「时间窗 → 重复 → 校验项 → 迟到/早退」，故越窗必须先于去重判定，否则同条件会给出不同错误码。
     *
     * @return 200 在窗内 / 9102 越窗
     */
    public static int windowCode(ClockFacts f) {
        double from;
        double to;
        if (f.usePeriod()) {
            from = f.startMin() - f.allowEarlyMin();
            to = f.endMin() + f.allowLateMin();
        } else if (AttendanceConstants.CHECK_TYPE_ON.equals(f.checkType())) {
            from = f.startMin() - AttendanceConstants.OPEN_AHEAD_MIN;
            to = f.endMin();
        } else {
            from = f.startMin();
            to = f.endMin() + AttendanceConstants.CLOSE_DELAY_MIN;
        }
        // 注意：startMin/endMin 为 NaN（脏数据）时比较恒为 false → 不触发越窗，与 Mock 等价
        if (f.enableTimeWindow() && (f.nowMinutes() < from || f.nowMinutes() > to)) {
            return 9102;
        }
        return 200;
    }

    /** 距离计算函数式接口：默认实现为 {@link HaversineCalculator}，生产实现为 {@code GeoService}（行为等价） */
    @FunctionalInterface
    public interface DistanceFn {
        double distance(Double lng1, Double lat1, Double lng2, Double lat2);
    }
}
