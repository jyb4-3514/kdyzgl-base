package com.qiujie.service.support.sms;

import com.qiujie.config.SmsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短信通道启动守卫单测（M2）：生产 fail-fast（未配置 Key 不得静默降级）；
 * M4+ 追加：生产环境禁止配置万能验证码（{@code hrm.sms.dev-universal-code} 非空即 fail-fast）。
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

    @Test
    @DisplayName("isUniversalCodeConfigured：null / 空串 / 纯空白 → 关闭；非空 → 启用")
    void universalCodeConfiguredBranches() {
        assertFalse(SmsConfigGuard.isUniversalCodeConfigured(null));
        SmsProperties p = new SmsProperties();
        assertFalse(SmsConfigGuard.isUniversalCodeConfigured(p));
        p.setDevUniversalCode("");
        assertFalse(SmsConfigGuard.isUniversalCodeConfigured(p));
        p.setDevUniversalCode("   ");
        assertFalse(SmsConfigGuard.isUniversalCodeConfigured(p));
        p.setDevUniversalCode("000000");
        assertTrue(SmsConfigGuard.isUniversalCodeConfigured(p));
    }

    @Test
    @DisplayName("生产 fail-fast：万能验证码非空即抛 IllegalStateException，且错误信息不回显码值")
    void prodRejectsUniversalCode() {
        SmsProperties p = props("aliyun", "real-id", "real-secret");
        p.setDevUniversalCode("000000");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SmsConfigGuard.validateNoUniversalCode(p));
        assertTrue(ex.getMessage().contains("生产环境禁止配置万能验证码"));
        // 验证码红线：异常信息不得出现码值
        assertFalse(ex.getMessage().contains("000000"));
    }

    @Test
    @DisplayName("生产通过：万能验证码为空 → 不抛异常")
    void prodAllowsEmptyUniversalCode() {
        SmsProperties p = props("aliyun", "real-id", "real-secret");
        p.setDevUniversalCode("");
        SmsConfigGuard.validateNoUniversalCode(p);
    }

    @Test
    @DisplayName("环境判定口径：prod profile → isProd=true；无 profile / null → false")
    void isProdByProfile() {
        assertTrue(SmsConfigGuard.isProd(prodEnvironment()));
        assertFalse(SmsConfigGuard.isProd(new MockEnvironment()));
        assertFalse(SmsConfigGuard.isProd(null));
    }

    private static MockEnvironment prodEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }
}
