package com.qiujie.service.support.sms;

/**
 * 短信发送端口（ADR-MC-04 三件套之「端口」）。
 * <p>
 * 生产实现 {@code AliyunSmsSender}（仅在 Key 配置有效时装配）；降级实现 {@code LoggingSmsSender}
 * （未配置 Key，仅日志、不真正发送；生产环境由 {@code SmsConfigGuard} 启动期 fail-fast 拦截）。
 * <p>
 * <b>调用方约定</b>（M4 落地）：验证码的生成与 Redis 暂存由业务服务负责，本端口只负责「把给定的验证码投递出去」；
 * 实现<b>不得</b>将 {@code code} 写入日志/异常/响应。
 */
public interface SmsSender {

    /**
     * 发送验证码短信。
     *
     * @param phone 手机号（实现内<b>必须脱敏</b>后才可入日志）
     * @param scene 业务场景（决定模板）
     * @param code  验证码明文（仅在「服务端 → 短信通道」一次流转；严禁入日志/异常/响应）
     * @return 发送结果（不含验证码与上游原始报错）
     */
    SmsSendResult send(String phone, SmsScene scene, String code);
}
