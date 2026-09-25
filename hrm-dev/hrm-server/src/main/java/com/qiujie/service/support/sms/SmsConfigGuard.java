package com.qiujie.service.support.sms;

import com.qiujie.config.SmsProperties;
import com.qiujie.config.condition.ExternalAdapterConditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 短信通道启动期守卫（安全要求：<b>未配置 Key 时代码服务 fail-closed，禁止弱兜底</b>）。
 * <p>
 * 判定口径（security-auth-review §4.4 第 ⑥ 条、架构 ADR-MC-04）：
 * <ul>
 *   <li><b>生产（prod）</b>：必须配置有效短信凭据（{@code hrm.sms.provider=aliyun} 且 AccessKey 非占位）；
 *       否则<b>启动失败</b>（抛 {@link IllegalStateException}），避免生产误用降级通道静默放行；
 *       且 {@code hrm.sms.dev-universal-code} 必须为空，非空同样<b>启动失败</b>（测试后门不得进生产）；</li>
 *   <li><b>非生产（dev/test/local）</b>：允许降级（{@link LoggingSmsSender}，仅日志不发送）；
 *       若误配 {@code provider=aliyun} 但 Key 缺失，仅告警提示，不阻断启动；
 *       若配置了万能验证码则打 WARN 明确提示「万能验证码已启用（仅测试环境）」（不回显码值）。</li>
 * </ul>
 * 校验逻辑抽为静态方法（{@link #validateProd}、{@link #validateNoUniversalCode}、{@link #isProd}），
 * 便于离线单测覆盖各分支（本机无 JDK/Maven，收敛到服务器阶段运行）。
 */
@Slf4j
@Component
public class SmsConfigGuard {

    /** 生产环境降级禁用提示（不含任何凭据） */
    public static final String ERROR_PROD_SMS_NOT_CONFIGURED =
            "生产环境禁止短信降级：hrm.sms.provider 必须为 aliyun 且 hrm.sms.aliyun.access-key-id/secret 已配置有效值"
                    + "（未配置即启动失败，安全要求 fail-closed；真实凭据由主智能体托管下发）";

    /** 生产环境万能验证码禁用提示（不含任何码值） */
    public static final String ERROR_PROD_UNIVERSAL_CODE =
            "生产环境禁止配置万能验证码：hrm.sms.dev-universal-code 仅供测试环境使用，生产必须为空字符串；"
                    + "已检测到该键非空，启动失败（安全要求 fail-closed，避免测试后门随配置泄露进生产）";

    /**
     * 构造期即校验（Bean 创建失败 = 应用启动失败），无需 {@code @PostConstruct}。
     */
    public SmsConfigGuard(Environment environment, SmsProperties properties) {
        boolean prod = isProd(environment);
        if (prod) {
            validateProd(properties);
            // 生产禁止万能码：与「生产未配短信凭据即失败」同等强度（fail-fast）
            validateNoUniversalCode(properties);
            log.info("短信通道生产校验通过：provider=aliyun（凭据已配置，不打印具体值）");
        } else {
            warnIfUniversalCodeEnabled(properties);
            if (!isConfigured(properties)) {
                log.warn("短信通道未配置有效凭据，当前为非生产环境，将使用降级通道（仅日志不发送，不打印验证码明文）");
            }
        }
    }

    /** 环境判定口径（与短信降级判定同源，供 {@code AuthServiceImpl} 复用）：prod profile 视为生产 */
    public static boolean isProd(Environment environment) {
        return environment != null && environment.acceptsProfiles(Profiles.of("prod"));
    }

    /** 生产环境校验：未配置有效短信用途凭据即抛出（fail-fast） */
    public static void validateProd(SmsProperties properties) {
        if (!isConfigured(properties)) {
            throw new IllegalStateException(ERROR_PROD_SMS_NOT_CONFIGURED);
        }
    }

    /** 生产环境校验：万能验证码非空即抛出（fail-fast，错误信息不回显码值） */
    public static void validateNoUniversalCode(SmsProperties properties) {
        if (isUniversalCodeConfigured(properties)) {
            throw new IllegalStateException(ERROR_PROD_UNIVERSAL_CODE);
        }
    }

    /** 是否配置了万能验证码（非空即视为已启用；null / 空串 / 纯空白 = 关闭） */
    public static boolean isUniversalCodeConfigured(SmsProperties properties) {
        return properties != null && properties.getDevUniversalCode() != null
                && !properties.getDevUniversalCode().trim().isEmpty();
    }

    /** 非生产环境启动告警：只说明「已启用」，不回显码值（验证码红线） */
    private void warnIfUniversalCodeEnabled(SmsProperties properties) {
        if (isUniversalCodeConfigured(properties)) {
            log.warn("万能验证码已启用（仅测试环境）：hrm.sms.dev-universal-code 非空，"
                    + "登录/设备验证将接受该码；生产环境禁止配置该键（非空即启动失败）");
        }
    }

    /** 是否已配置有效短信凭据（provider=aliyun 且 AccessKey 齐备非占位） */
    public static boolean isConfigured(SmsProperties properties) {
        if (properties == null || properties.getAliyun() == null) {
            return false;
        }
        return ExternalAdapterConditions.aliyunSmsConfigured(
                properties.getProvider(),
                properties.getAliyun().getAccessKeyId(),
                properties.getAliyun().getAccessKeySecret());
    }
}
