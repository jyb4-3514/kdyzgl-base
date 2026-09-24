package com.qiujie.service.support.geo.impl;

import com.qiujie.service.attendance.support.HaversineCalculator;
import com.qiujie.service.support.geo.GeoAddress;
import com.qiujie.service.support.geo.GeoService;
import org.springframework.stereotype.Component;

/**
 * Haversine 地理实现（默认 / 降级；ADR-MC-04「降级实现」）。
 * <p>
 * 把现有 P3 考勤使用的 {@link HaversineCalculator} 提取为端口实现（<b>逐位等价</b>，不改变任何判定结果）：
 * 距离仍为真实球面距离；逆地理编码在降级下不可用（返回 {@code null}），仅做围栏校验，不阻断打卡。
 */
@Component
public class HaversineGeoService implements GeoService {

    /** 供应商标识 */
    public static final String PROVIDER = "haversine";

    @Override
    public GeoAddress regeo(Double longitude, Double latitude) {
        // 降级实现不做逆地理编码（无第三方 Key）；地址为展示字段，缺失不影响围栏判定
        return null;
    }

    @Override
    public double distance(Double lng1, Double lat1, Double lng2, Double lat2) {
        return HaversineCalculator.distance(lng1, lat1, lng2, lat2);
    }

    @Override
    public boolean inFence(Double centerLng, Double centerLat, Double pointLng, Double pointLat, Integer radiusMeters) {
        if (radiusMeters == null) {
            return false; // 无半径视为不命中（与现有考勤口径一致）
        }
        double meters = distance(centerLng, centerLat, pointLng, pointLat);
        return Double.isFinite(meters) && meters <= radiusMeters;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }
}
