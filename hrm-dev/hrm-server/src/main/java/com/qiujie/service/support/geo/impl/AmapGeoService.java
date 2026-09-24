package com.qiujie.service.support.geo.impl;

import com.qiujie.config.GeoProperties;
import com.qiujie.config.condition.ExternalAdapterConditions;
import com.qiujie.service.attendance.support.HaversineCalculator;
import com.qiujie.service.support.geo.GeoAddress;
import com.qiujie.service.support.geo.GeoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 高德地理实现（生产实现；架构 §4.5）。
 * <p>
 * 装配条件：{@code hrm.geo.provider=amap} 且 {@code hrm.geo.amap.key} 已配置且非占位
 * （{@link ExternalAdapterConditions.AmapConfigured}）；<b>Key 未配置时不被装配</b>，回落 {@link HaversineGeoService}。
 * <p>
 * <b>本批状态</b>：仅落「端口实现 + 装配条件 + 距离/围栏委托」骨架，<b>逆地理编码尚未接入高德 Web 服务</b>
 * （见 {@link #regeo} 的 {@code TODO(扩展)}）。原因：本机无 JDK/Maven，无法验证出网调用与 Key 签名；
 * 且本批无调用方，不接入不影响任何契约与考勤行为。
 * <p>
 * <b>行为等价保证</b>：{@link #distance}/{@link #inFence} 恒委托 {@link HaversineCalculator}（本地球面距离），
 * 与降级实现完全一致 → 切换供应商不改变考勤围栏判定结果（满足「行为与现状等价、不改考勤契约」）。
 * <p>
 * <b>凭据红线</b>：Key/安全码仅服务端持有、由主智能体托管，日志只输出是否已配置，绝不打印 Key 明文。
 */
@Slf4j
@Component
@Primary
@Conditional(ExternalAdapterConditions.AmapConfigured.class)
public class AmapGeoService implements GeoService {

    /** 供应商标识 */
    public static final String PROVIDER = "amap";

    private final GeoProperties properties;

    public AmapGeoService(GeoProperties properties) {
        this.properties = properties;
        // 仅输出配置存在性与超时配置，绝不打印 Key
        log.info("高德定位通道已装配（provider=amap，Key 长度={}，超时={}ms）",
                properties.getAmap().getKey() == null ? 0 : properties.getAmap().getKey().length(),
                properties.getTimeoutMillis());
    }

    @Override
    public GeoAddress regeo(Double longitude, Double latitude) {
        // TODO(扩展): 调用高德 Web 服务逆地理编码（/v3/geocode/regeo，带 Key 与数字签名 security-code，
        //   超时 hrm.geo.timeout-millis），结果用于展示/审计；Key 由主智能体托管，禁止下发前端。
        //   接入前返回 null（等价于「无地址」，与降级实现一致），不影响围栏判定。
        log.debug("高德逆地理编码尚未接入，本次不返回地址（longitude={}, latitude={}）", longitude, latitude);
        return null;
    }

    @Override
    public double distance(Double lng1, Double lat1, Double lng2, Double lat2) {
        // 距离/围栏一律本地计算，保证与降级实现等价；高德仅补逆地理编码（展示）
        return HaversineCalculator.distance(lng1, lat1, lng2, lat2);
    }

    @Override
    public boolean inFence(Double centerLng, Double centerLat, Double pointLng, Double pointLat, Integer radiusMeters) {
        if (radiusMeters == null) {
            return false;
        }
        double meters = distance(centerLng, centerLat, pointLng, pointLat);
        return Double.isFinite(meters) && meters <= radiusMeters;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }
}
