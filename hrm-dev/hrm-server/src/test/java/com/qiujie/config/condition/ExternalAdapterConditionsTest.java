package com.qiujie.config.condition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部适配器装配条件单测（M2）：provider 开关 + 凭据有效性判定。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class ExternalAdapterConditionsTest {

    @Test
    @DisplayName("configured：null/空/空白/占位符 判为未配置；真实值判为已配置")
    void configured() {
        assertFalse(ExternalAdapterConditions.configured(null));
        assertFalse(ExternalAdapterConditions.configured(""));
        assertFalse(ExternalAdapterConditions.configured("   "));
        assertFalse(ExternalAdapterConditions.configured("change_me_geo_amap_key"));
        assertFalse(ExternalAdapterConditions.configured("  change_me_x  "));
        assertTrue(ExternalAdapterConditions.configured("real-key"));
        assertTrue(ExternalAdapterConditions.configured("  real-key  "));
    }

    @Test
    @DisplayName("aliyunSmsConfigured：provider=aliyun 且 AccessKey 齐备非占位才为 true（大小写不敏感）")
    void aliyunSmsConfigured() {
        assertTrue(ExternalAdapterConditions.aliyunSmsConfigured("aliyun", "id", "secret"));
        assertTrue(ExternalAdapterConditions.aliyunSmsConfigured("ALIYUN", "id", "secret"));
        assertFalse(ExternalAdapterConditions.aliyunSmsConfigured("none", "id", "secret"));
        assertFalse(ExternalAdapterConditions.aliyunSmsConfigured(null, "id", "secret"));
        assertFalse(ExternalAdapterConditions.aliyunSmsConfigured("aliyun", "", "secret"));
        assertFalse(ExternalAdapterConditions.aliyunSmsConfigured("aliyun", "id", "change_me_x"));
    }

    @Test
    @DisplayName("amapConfigured：provider=amap 且 Key 已配置非占位才为 true（Key 未配置即不装配）")
    void amapConfigured() {
        assertTrue(ExternalAdapterConditions.amapConfigured("amap", "real-key"));
        assertFalse(ExternalAdapterConditions.amapConfigured("haversine", "real-key"));
        assertFalse(ExternalAdapterConditions.amapConfigured("amap", ""));
        assertFalse(ExternalAdapterConditions.amapConfigured("amap", "change_me_geo_amap_key"));
        assertFalse(ExternalAdapterConditions.amapConfigured(null, "real-key"));
    }
}
