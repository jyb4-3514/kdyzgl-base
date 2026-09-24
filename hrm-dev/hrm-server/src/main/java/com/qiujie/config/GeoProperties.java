package com.qiujie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 地理定位强类型配置（{@code hrm.geo.*}，架构 multi-client-architecture §3.5 / §4.5）。
 * <p>
 * <b>凭据红线</b>：高德 Key 仅服务端持有、由主智能体托管，仓库内只写 {@code change_me_*} 占位符；
 * 后端代转发高德 Web 服务 API，<b>不下发前端</b>（security-auth-review §4.6）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.geo")
public class GeoProperties {

    /**
     * 定位供应商：{@code amap}（高德，需 Key）| {@code haversine}（本地球面距离，默认/降级）。
     * 默认 {@code haversine}：无 Key 亦可运行；{@code amap} 仅在 Key 已配置时才装配（见 {@code ExternalAdapterConditions}）。
     */
    private String provider = "haversine";

    private Amap amap = new Amap();

    /**
     * 围栏半径兜底默认值（米）。默认 200。
     * <b>本批未接线</b>：考勤围栏实际半径以 {@code attendance_rule.radius} 为准；规则未配半径时现状为
     * 「不命中（9104）」，若在此接入兜底会改变既有 9104 语义（违反「不改动契约/行为等价」），故留待 M4 契约裁定。
     */
    private int fenceRadiusMeters = 200;

    /**
     * 高德 HTTP 调用超时（毫秒）。默认 3000；取值范围：正整数。
     * 本批仅承载配置，供后续接入实现使用。
     */
    private int timeoutMillis = 3000;

    /** 高德通道参数（占位符，真实值由主智能体托管下发） */
    @Data
    public static class Amap {
        /** Web 服务 Key（占位符） */
        private String key = "";
        /** 数字签名安全码（占位符，可选） */
        private String securityCode = "";
    }
}
