package com.qiujie.service.attendance.support;

/**
 * Haversine 大圆距离（米）。判定电子围栏用真实球面距离而非经纬度差值（后者在经度方向会明显失真）。
 * <p>
 * 与 Mock {@code attendanceStore.haversine} 逐位等价：{@code 2·R·asin(min(1, sqrt(a)))}，
 * R=6371000（地球平均半径），保证「同一坐标 → 同一距离」可对照。
 */
public final class HaversineCalculator {

    /** 地球平均半径（米） */
    public static final double EARTH_RADIUS_METERS = 6371000d;

    private HaversineCalculator() {
    }

    /**
     * 计算两点球面距离（米）。任一入参为 null 或结果非有限值时返回 {@link Double#NaN}
     * （对齐 Mock：{@code Number.isFinite(rawDistance)} 为 false 时 distance 置 null）。
     */
    public static double distance(Double lng1, Double lat1, Double lng2, Double lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null
                || !Double.isFinite(lng1) || !Double.isFinite(lat1)
                || !Double.isFinite(lng2) || !Double.isFinite(lat2)) {
            return Double.NaN;
        }
        double dLat = toRadians(lat2 - lat1);
        double dLng = toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private static double toRadians(double degrees) {
        return degrees * Math.PI / 180;
    }
}
