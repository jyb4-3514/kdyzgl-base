package com.qiujie.service.support.sms.impl;

import com.qiujie.config.SmsProperties;
import com.qiujie.config.condition.ExternalAdapterConditions;
import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsSendResult;
import com.qiujie.service.support.sms.SmsSender;
import com.qiujie.util.DesensitizeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信生产实现（ADR-MC-04 之「生产实现」；架构 §4.4.1）。
 * <p>
 * 装配条件：{@code hrm.sms.provider=aliyun} 且 AccessKey 已配置且非占位符
 * （{@link ExternalAdapterConditions.AliyunSmsConfigured}）；不满足时本 Bean 不装配，
 * 由 {@link LoggingSmsSender} 兜底（生产环境另有 {@code SmsConfigGuard} 启动期 fail-fast）。
 * <p>
 * <b>本批状态</b>：仅落「端口 + 装配条件 + 模板选择 + 日志脱敏」骨架，<b>尚未接入阿里云 SDK/HTTP 调用</b>
 * （见方法内 {@code TODO(扩展)}）。原因：本机无 JDK/Maven，无法对签名算法（HMAC-SHA1）与出网结果做任何验证，
 * 按「求证优先」不臆造不可验证的对接代码；且本批<b>无调用方</b>（短信端点属 M4），不接入不影响任何契约。
 * <p>
 * <b>日志红线</b>：绝不打印 {@code code}、AccessKey、手机号明文。
 */
@Slf4j
@Component
@Primary
@Conditional(ExternalAdapterConditions.AliyunSmsConfigured.class)
public class AliyunSmsSender implements SmsSender {

    /** 通道标识 */
    public static final String PROVIDER = "aliyun";
    /** 未接入实现时的失败原因（不含验证码/凭据/上游报文） */
    public static final String REASON_NOT_INTEGRATED = "ALIYUN_SMS_NOT_INTEGRATED";

    private final SmsProperties properties;

    public AliyunSmsSender(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SmsSendResult send(String phone, SmsScene scene, String code) {
        // 日志只含：场景、脱敏手机号、模板 Code（均非敏感）；不含 code / AccessKey / 手机号明文
        log.info("阿里云短信通道：请求发送（场景 {}，手机号 {}，签名 {}，模板 {}）",
                scene == null ? "UNKNOWN" : scene.name(),
                DesensitizeUtil.maskPhone(phone),
                properties.getAliyun().getSignName(),
                templateCode(scene));
        // TODO(扩展): 接入阿里云短信（SDK 坐标 com.aliyun:dysmsapi20170525 或官方 HTTP API + HMAC-SHA1 签名），
        //   完成 SendSms 调用并按响应码归类成功/失败（失败对外映射 1105，不暴露上游原始错误）。
        //   在接入并完成服务器端到端验证前，此处<b>显式返回失败</b>（绝不静默当作发送成功，避免成为绕过通道）。
        return SmsSendResult.fail(PROVIDER, REASON_NOT_INTEGRATED);
    }

    /** 场景 → 模板 Code（缺失返回 null，交由接入实现识别为配置不完整） */
    private String templateCode(SmsScene scene) {
        if (scene == null) {
            return null;
        }
        SmsProperties.Aliyun aliyun = properties.getAliyun();
        return switch (scene) {
            case LOGIN -> aliyun.getTemplateCodeLogin();
            case DEVICE_VERIFY -> aliyun.getTemplateCodeDevice();
            case PERIODIC_REAUTH -> aliyun.getTemplateCodeReauth();
            case REGISTER -> aliyun.getTemplateCodeRegister();
        };
    }
}
