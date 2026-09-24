package com.qiujie.service.support.sms;

import com.qiujie.config.SmsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短信通道启动守卫单测（M2）：生产 fail-fast（未配置 Key 不得静默降级）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SmsConfigGuardTest {

    private static SmsProperties props(String provider, String keyId, String keySecret) {
        SmsProperties properties = new SmsProperties();
        properties.setProvider(provider);
        properties.getAliyun().setAccessKeyId(keyId);
        properties.getAliyun().setAccessKeySecret(keySecret);
        return properties;
    }

    @Test
    @DisplayName("isConfigured：provider!=aliyun / Key 为空 / Key 为占位符 一律视为未配置")
    void isConfiguredFalseBranches() {
        assertFalse(SmsConfigGuard.isConfigured(null));
        assertFalse(SmsConfigGuard.isConfigured(props("none", "real-id", "real-secret")));
        assertFalse(SmsConfigGuard.isConfigured(props("aliyun", "", "")));
        assertFalse(SmsConfigGuard.isConfigured(props("aliyun", "change_me_sms_access_key_id", "real-secret")));
        assertFalse(SmsConfigGuard.isConfigured(props("aliyun", "real-id", "change_me_sms_access_key_secret")));
    }

    @Test
    @DisplayName("isConfigured：provider=aliyun 且 AccessKey 齐备非占位 → 已配置")
    void isConfiguredTrue() {
        assertTrue(SmsConfigGuard.isConfigured(props("aliyun", "real-id", "real-secret")));
        assertTrue(SmsConfigGuard.isConfigured(props("ALIYUN", "real-id", "real-secret")));
    }

    @Test
    @DisplayName("生产 fail-fast：未配置有效 Key 即抛 IllegalStateException（禁止降级放行）")
    void validateProdFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SmsConfigGuard.validateProd(props("none", "", "")));
        assertTrue(ex.getMessage().contains("生产环境禁止短信降级"));
        assertThrows(IllegalStateException.class,
                () -> SmsConfigGuard.validateProd(props("aliyun", "change_me_x", "change_me_y")));
    }

    @Test
    @DisplayName("生产校验通过：已配置有效凭据不抛异常")
    void validateProdPasses() {
        SmsConfigGuard.validateProd(props("aliyun", "real-id", "real-secret"));
    }
}
