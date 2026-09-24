package com.qiujie.service.support.sms;

/**
 * 短信验证码校验决策（纯逻辑，可离线单测）——<b>逐条对齐前端已实现的 Mock</b>
 * {@code hrm-demo/src/shared/mock/routes/auth.js#verifyCode} 的判定顺序。
 * <p>
 * 判定顺序（<b>顺序即语义，不得调换</b>）：
 * <ol>
 *   <li>失败次数已达上限 → {@link Decision#ATTEMPTS_EXCEEDED}（1103，验证码作废）；</li>
 *   <li>比对失败 → 记一次失败；若加上本次即达上限 → {@code ATTEMPTS_EXCEEDED}，否则 {@link Decision#INVALID}（1102）；</li>
 *   <li>恒等 → {@link Decision#OK}（调用方随即删除验证码，保证一次性）。</li>
 * </ol>
 * 比较走 {@link SmsCodeGenerator#constantTimeEquals}（恒定时间，防时序侧信道）。
 * TTL / 验证码不存在的判定由调用方（Redis 层）负责：取不到码或已过期一律 1102。
 */
public final class SmsVerifyPolicy {

    /** 校验结论 */
    public enum Decision {
        /** 校验通过（须立即删除验证码，保证一次性） */
        OK,
        /** 验证码错误或已过期 → 1102 */
        INVALID,
        /** 尝试次数达上限，本码作废 → 1103 */
        ATTEMPTS_EXCEEDED
    }

    private SmsVerifyPolicy() {
    }

    /**
     * 判定校验结果（不产生副作用；计数的写入与验证码删除由调用方完成）。
     *
     * @param expectedCode 服务端持有的验证码（{@code null} = 无有效验证码 → 视同失败）
     * @param actualCode   用户提交的验证码（可含前后空白）
     * @param attempts     本次之前已失败次数
     * @param maxAttempts  失败上限（{@code hrm.sms.max-verify-attempts}；{@code <=0} 视为不限）
     */
    public static Decision verify(String expectedCode, String actualCode, int attempts, int maxAttempts) {
        if (maxAttempts > 0 && attempts >= maxAttempts) {
            return Decision.ATTEMPTS_EXCEEDED;
        }
        String actual = actualCode == null ? null : actualCode.trim();
        if (!SmsCodeGenerator.constantTimeEquals(expectedCode, actual)) {
            return (maxAttempts > 0 && attempts + 1 >= maxAttempts)
                    ? Decision.ATTEMPTS_EXCEEDED
                    : Decision.INVALID;
        }
        return Decision.OK;
    }
}
