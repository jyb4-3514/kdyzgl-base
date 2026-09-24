package com.qiujie.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 外部适配器装配条件（ADR-MC-04「端口 + 生产实现 + 降级实现」三件套）。
 * <p>
 * 生产实现的装配以「<b>确实配置了有效凭据</b>」为前提，而非仅看 {@code provider} 开关：
 * <ul>
 *   <li>{@code amap} 高德：{@code hrm.geo.provider=amap} 且 {@code hrm.geo.amap.key} 已配置（非空、非 {@code change_me_*} 占位）→ 装配
 *       {@code AmapGeoService}（Key 未配置时<b>不被装配</b>，回落默认 Haversine，安全评估 §4.6）；</li>
 *   <li>{@code aliyun} 短信：{@code hrm.sms.provider=aliyun} 且 AccessKeyId/Secret 均有效 → 装配 {@code AliyunSmsSender}。</li>
 * </ul>
 * 「有效」的判定抽为静态方法，便于离线单测覆盖各分支（本机无 JDK/Maven，收敛到服务器阶段运行）。
 */
public final class ExternalAdapterConditions {

    /** 占位符前缀（仓库内只允许写占位符，真实凭据由主智能体托管下发） */
    public static final String PLACEHOLDER_PREFIX = "change_me";

    private ExternalAdapterConditions() {
    }

    /** 配置值是否为「已配置的有效值」：非空、非占位符 */
    public static boolean configured(String value) {
        return value != null && !value.isBlank() && !value.trim().startsWith(PLACEHOLDER_PREFIX);
    }

    /** 阿里云短信通道是否可装配（provider=aliyun 且 AccessKey 齐备且非占位） */
    public static boolean aliyunSmsConfigured(String provider, String accessKeyId, String accessKeySecret) {
        return "aliyun".equalsIgnoreCase(trim(provider))
                && configured(accessKeyId)
                && configured(accessKeySecret);
    }

    /** 高德地理通道是否可装配（provider=amap 且 Key 已配置且非占位） */
    public static boolean amapConfigured(String provider, String key) {
        return "amap".equalsIgnoreCase(trim(provider)) && configured(key);
    }

    /** {@code AmapGeoService} 装配条件 */
    public static final class AmapConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            return amapConfigured(env.getProperty("hrm.geo.provider"), env.getProperty("hrm.geo.amap.key"));
        }
    }

    /** {@code AliyunSmsSender} 装配条件 */
    public static final class AliyunSmsConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            return aliyunSmsConfigured(env.getProperty("hrm.sms.provider"),
                    env.getProperty("hrm.sms.aliyun.access-key-id"),
                    env.getProperty("hrm.sms.aliyun.access-key-secret"));
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
