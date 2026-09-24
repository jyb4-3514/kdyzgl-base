package com.qiujie.service.auth.support;

import com.qiujie.config.AuthProperties;
import com.qiujie.config.condition.ExternalAdapterConditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 服务端设备指纹（HMAC-SHA256，盐外置）。
 * <p>
 * <b>安全定位（必读）</b>：指纹的输入（deviceId / platform / model / osVersion / UA）全部来自客户端，
 * <b>可被伪造或重放</b>，故指纹<b>只用于幂等登记与审计</b>，<b>绝不作为放行依据</b>
 * （security-auth-review §4.2 高危缺陷整改）。真正的「是否为受信设备」判定依赖服务端签发的
 * {@code device_token} 摘要（见 {@link DeviceTokenCodec}）。
 * <p>
 * 盐 {@code hrm.auth.device-fingerprint-salt} 为服务端秘密：仓库只写 {@code change_me_*} 占位符，
 * 真实值落服务器外置配置；<b>生产 profile 下未配置有效盐即启动失败</b>（fail-closed，与 {@code SmsConfigGuard} 同口径），
 * 避免弱盐/空盐静默上线导致指纹可被离线复现。
 */
@Slf4j
@Component
public class DeviceFingerprint {

    /** 生产环境盐缺失提示（不含任何凭据） */
    public static final String ERROR_PROD_SALT_NOT_CONFIGURED =
            "生产环境禁止空盐/占位盐：hrm.auth.device-fingerprint-salt 必须配置为有效随机串"
                    + "（未配置即启动失败，安全要求 fail-closed；真实值由主智能体托管下发）";

    /** UA 归一化后保留的最大长度（避免超长 UA 撑大 HMAC 输入；表列 CHAR(64) 只存摘要，与此无关） */
    private static final int UA_MAX_LENGTH = 120;

    private final String salt;

    public DeviceFingerprint(AuthProperties authProperties, Environment environment) {
        String configured = authProperties.getDeviceFingerprintSalt();
        this.salt = configured == null ? "" : configured.trim();
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod && !ExternalAdapterConditions.configured(salt)) {
            throw new IllegalStateException(ERROR_PROD_SALT_NOT_CONFIGURED);
        }
        if (!ExternalAdapterConditions.configured(salt)) {
            log.warn("设备指纹盐未配置有效值（当前非生产）：指纹仅用于幂等登记/审计，生产环境将启动失败");
        }
    }

    /**
     * 计算设备指纹（hex，64 字符，落 {@code auth_trusted_device.device_fingerprint}）。
     *
     * @param deviceId   前端上报设备标识（弱信号，可空）
     * @param platform   端平台（ANDROID/IOS/H5/WEB，可空）
     * @param model      设备型号（弱信号，可空）
     * @param osVersion  系统版本（弱信号，可空）
     * @param userAgent  请求头 User-Agent（服务端取值，不信任前端传值；可为空）
     * @return HMAC-SHA256 hex 指纹；输入全空时仍返回确定值（空串的 HMAC），保证幂等
     */
    public String compute(String deviceId, String platform, String model, String osVersion, String userAgent) {
        String message = String.join("|",
                blankToEmpty(deviceId),
                blankToEmpty(platform),
                blankToEmpty(model),
                blankToEmpty(osVersion),
                canonicalUa(userAgent));
        return hmacSha256Hex(salt, message);
    }

    /**
     * UA 归一化（降噪，降低同一设备因版本升级导致指纹漂移的概率）：
     * 转小写、剔除数字与点号构成的版本号、折叠空白、截断至 {@value #UA_MAX_LENGTH} 字符。
     * <p>为什么只做粗归一：更强的归一会引入碰撞，反而让不同设备判为同一指纹（弱信号误判）。
     */
    public static String canonicalUa(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT)
                .replaceAll("\\d+(\\.\\d+)*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() > UA_MAX_LENGTH ? normalized.substring(0, UA_MAX_LENGTH) : normalized;
    }

    /** HMAC-SHA256 → hex（盐为空时按 RFC 2104 以空密钥计算；生产已由构造期守卫禁止空盐） */
    static String hmacSha256Hex(String salt, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((salt == null ? "" : salt).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("设备指纹计算失败（HmacSHA256 不可用）", e);
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hex[i * 2] = digits[value >>> 4];
            hex[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(hex);
    }
}
