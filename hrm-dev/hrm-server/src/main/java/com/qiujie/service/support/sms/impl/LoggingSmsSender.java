package com.qiujie.service.support.sms.impl;

import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsSendResult;
import com.qiujie.service.support.sms.SmsSender;
import com.qiujie.util.DesensitizeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信降级实现（未配置厂商 Key 时的本地通道；ADR-MC-04 之「降级实现」）。
 * <p>
 * <b>不做弱兜底</b>：不真正发送短信、不产生可用凭据，仅<b>记录一条脱敏日志</b>并返回成功，
 * 便于开发/联调阶段跑通流程；<b>生产环境由 {@code SmsConfigGuard} 启动期 fail-fast 拦截</b>，
 * 绝不允许以该通道静默放行（security-auth-review §4.4 第 ⑥ 条要求 fail-closed）。
 * <p>
 * <b>日志红线</b>：只打印「已生成 N 位验证码」与<b>脱敏手机号</b>，<b>绝不打印验证码明文</b>。
 * <p>
 * 装配：作为<b>兜底 Bean</b> 始终存在；当 {@code AliyunSmsSender} 满足装配条件（@Primary）时由其实例优先注入。
 */
@Slf4j
@Component
public class LoggingSmsSender implements SmsSender {

    /** 通道标识（出现在 {@link SmsSendResult} 中，供审计区分实际通道） */
    public static final String PROVIDER = "logging";

    @Override
    public SmsSendResult send(String phone, SmsScene scene, String code) {
        int codeLength = code == null ? 0 : code.length();
        // 只输出脱敏手机号与位数，不含 code 明文
        log.info(buildLogMessage(DesensitizeUtil.maskPhone(phone), scene, codeLength));
        return SmsSendResult.ok(PROVIDER);
    }

    /**
     * 组装降级日志文案（抽出为静态方法以便单测断言「不含验证码明文」）。
     * 注意：入参 {@code maskedPhone} 必须为<b>已脱敏</b>手机号；{@code codeLength} 为验证码位数而非内容。
     */
    static String buildLogMessage(String maskedPhone, SmsScene scene, int codeLength) {
        return "短信降级通道：已生成 " + codeLength + " 位验证码并投递至 " + maskedPhone
                + "（场景 " + (scene == null ? "UNKNOWN" : scene.name())
                + "，降级模式不真正发送，且不打印验证码明文）";
    }
}
