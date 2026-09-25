package com.qiujie.service.support.sms;

/**
 * 测试环境「万能验证码」判定（纯逻辑，可离线单测）。
 * <p>
 * 解决的问题：降级通道的 {@code hrm.sms.dev-fixed-code} 只决定「生成出来的验证码是什么」，
 * 仍须<b>先点「获取验证码」</b>落 Redis 再比对；用户不点发码直接输码时取不到码 → 1102。
 * 本策略提供「不用先发码也能过」的放行判定，用于测试/联调环境。
 * <p>
 * <b>三条件同时满足才生效</b>（任一不满足即返回 {@code false}，完全按原有逻辑走）：
 * <ol>
 *   <li>配置值非空（去空白后非空）；</li>
 *   <li>非生产环境（由调用方按 {@link SmsConfigGuard#isProd} 口径判定后以 {@code production} 传入）；</li>
 *   <li>请求提交的 {@code code} 去空白后与该配置值<b>恒等</b>——走
 *       {@link SmsCodeGenerator#constantTimeEquals} 恒定时间比较，<b>区分大小写</b>。</li>
 * </ol>
 * <p>
 * <b>无副作用</b>：本类不访问 Redis、不读写任何计数，仅做字符串判定。放行后是否消耗验证码 /
 * 尝试计数由调用方决定——调用方在放行分支<b>不得</b>触碰存储层（即「不消耗任何码与计数」）。
 * <p>
 * <b>红线</b>：本类不打印任何内容（配置值即万能码，绝不进日志 / 异常 / 响应体）。
 */
public final class SmsUniversalCodePolicy {

    private SmsUniversalCodePolicy() {
    }

    /**
     * 万能验证码是否放行。
     *
     * @param configuredCode {@code hrm.sms.dev-universal-code}（可为 null / 空 / 含前后空白）
     * @param production     是否生产环境（true 时无条件不生效）
     * @param submittedCode  请求提交的 code（可为 null / 空 / 含前后空白）
     */
    public static boolean isActive(String configuredCode, boolean production, String submittedCode) {
        if (production) {
            return false;
        }
        String configured = trimToEmpty(configuredCode);
        if (configured.isEmpty()) {
            return false;
        }
        String submitted = trimToEmpty(submittedCode);
        if (submitted.isEmpty()) {
            return false;
        }
        // 恒等比较（区分大小写，恒定时间）；任一侧为 null 已在上方短路，此处无 NPE 风险。
        return SmsCodeGenerator.constantTimeEquals(configured, submitted);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
