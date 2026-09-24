package com.qiujie.service.auth.support;

import com.qiujie.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务端设备指纹单测（M4）：HMAC 确定性/盐敏感性/长度、UA 归一、生产空盐 fail-fast。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class DeviceFingerprintTest {

    @Test
    @DisplayName("HMAC-SHA256：确定性、64 位 hex；不同盐 / 不同消息 → 不同指纹")
    void hmacDeterministicAndSaltSensitive() {
        String a = DeviceFingerprint.hmacSha256Hex("salt-a", "dev|WEB|model|os|ua");
        assertEquals(64, a.length());
        assertTrue(a.matches("[0-9a-f]{64}"));
        assertEquals(a, DeviceFingerprint.hmacSha256Hex("salt-a", "dev|WEB|model|os|ua"));
        assertNotEquals(a, DeviceFingerprint.hmacSha256Hex("salt-b", "dev|WEB|model|os|ua"));
        assertNotEquals(a, DeviceFingerprint.hmacSha256Hex("salt-a", "dev|WEB|model|os|ua2"));
    }

    @Test
    @DisplayName("UA 归一：剔除版本号、折叠空白、转小写；空值返回空串")
    void canonicalUaStripsVersionNoise() {
        assertEquals("", DeviceFingerprint.canonicalUa(null));
        assertEquals("", DeviceFingerprint.canonicalUa("   "));
        String canonical = DeviceFingerprint.canonicalUa("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0");
        assertTrue(canonical.contains("mozilla/"), "应转小写，实际=" + canonical);
        assertTrue(!canonical.contains("131"), "应剔除版本号，实际=" + canonical);
        assertTrue(!canonical.contains("  "), "应折叠空白，实际=" + canonical);
    }

    @Test
    @DisplayName("生产 profile + 占位盐 → 构造期 fail-fast（fail-closed）")
    void prodPlaceholderSaltFailsFast() {
        AuthProperties properties = new AuthProperties();
        properties.setDeviceFingerprintSalt("change_me_device_fingerprint_salt");
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> new DeviceFingerprint(properties, prodEnv));
    }

    @Test
    @DisplayName("生产 profile + 空盐 → fail-fast；非生产 + 占位盐 → 允许（仅告警）")
    void prodEmptySaltFailsFastButDevAllows() {
        AuthProperties properties = new AuthProperties();
        properties.setDeviceFingerprintSalt("");
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new DeviceFingerprint(properties, prodEnv));

        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        // 不抛异常即通过（构造期只告警）
        new DeviceFingerprint(properties, devEnv);
    }

    @Test
    @DisplayName("compute：入参全空仍返回确定值（幂等）；型号变化导致指纹变化")
    void computeIsDeterministic() {
        AuthProperties properties = new AuthProperties();
        properties.setDeviceFingerprintSalt("unit-test-salt");
        DeviceFingerprint fingerprint = new DeviceFingerprint(properties, new MockEnvironment());
        String empty = fingerprint.compute(null, null, null, null, null);
        assertEquals(empty, fingerprint.compute(null, null, null, null, null));
        assertNotEquals(empty, fingerprint.compute("dev-1", "WEB", null, null, null));
    }
}
