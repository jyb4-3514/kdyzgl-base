package com.qiujie.service.support.sms;

/**
 * 短信发送结果（端口出参）。
 * <p>
 * <b>红线</b>：结果<b>不含验证码</b>、不含上游原始报错（避免验证码经异常/日志外泄，security-auth-review §4.4 第 ⑦ 条）；
 * 失败原因仅用稳定的内部枚举文案，对外映射为业务码 1105「短信服务暂不可用」（M4 落地）。
 *
 * @param success    是否投递成功
 * @param provider   处理该次发送的通道（{@code aliyun} / {@code logging}）
 * @param failReason 失败原因（成功为 null；仅内部可读标识，不含验证码与凭据）
 */
public record SmsSendResult(boolean success, String provider, String failReason) {

    /** 构造成功结果 */
    public static SmsSendResult ok(String provider) {
        return new SmsSendResult(true, provider, null);
    }

    /** 构造失败结果（reason 不得包含验证码/凭据/上游原始报文） */
    public static SmsSendResult fail(String provider, String reason) {
        return new SmsSendResult(false, provider, reason);
    }
}
