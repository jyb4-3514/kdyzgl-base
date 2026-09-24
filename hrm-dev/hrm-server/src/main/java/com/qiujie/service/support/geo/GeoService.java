package com.qiujie.service.support.geo;

/**
 * 地理定位端口（ADR-MC-04 三件套之「端口」；架构 §4.5）。
 * <p>
 * 实现：
 * <ul>
 *   <li>{@code HaversineGeoService}——本地 Haversine 球面距离，<b>默认与降级</b>（无 Key 亦可运行，不阻断打卡）；</li>
 *   <li>{@code AmapGeoService}——高德 Web 服务（逆地理编码），仅在 Key 配置有效时装配（@Primary）。</li>
 * </ul>
 * <b>行为一致性</b>：{@link #distance}/{@link #inFence} 恒为本地球面距离计算（与现有 P3 考勤围栏等价），
 * 供应商差异仅体现在 {@link #regeo}（展示用）；因此切换供应商<b>不改变</b>考勤接口契约与判定结果。
 * <p>
 * <b>凭据红线</b>：高德 Key 仅服务端持有，后端代转发，<b>不下发前端</b>（security-auth-review §4.6）。
 */
public interface GeoService {

    /**
     * 逆地理编码（坐标 → 地址），仅作展示/审计。
     *
     * @return 地址信息；降级实现或不可用时返回 {@code null}
     */
    GeoAddress regeo(Double longitude, Double latitude);

    /**
     * 两点球面距离（米）。任一入参为 null 或结果非有限值时返回 {@link Double#NaN}
     * （调用方据此按「距离不可计算」处理，与现状一致）。
     */
    double distance(Double lng1, Double lat1, Double lng2, Double lat2);

    /**
     * 是否在电子围栏内（球面距离 ≤ 半径）。
     *
     * @param radiusMeters 围栏半径（米）；{@code null} 视为不命中（与现有考勤口径一致）
     */
    boolean inFence(Double centerLng, Double centerLat, Double pointLng, Double pointLat, Integer radiusMeters);

    /** 当前生效的供应商标识（{@code haversine} / {@code amap}），用于审计与排障 */
    String provider();
}
