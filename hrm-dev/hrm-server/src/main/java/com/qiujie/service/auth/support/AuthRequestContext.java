package com.qiujie.service.auth.support;

/**
 * 认证请求的传输层上下文（由 Controller 采集，Service 使用）。
 * <p>
 * 为什么用记录类而非直接把 {@code HttpServletRequest} 传入 Service：
 * ① 隔离 Servlet API，Service 层可纯逻辑单测；② 明确「服务端取值的不可信输入」边界——
 * {@code loginIp}/{@code userAgent} 取自请求头（审计用），{@code incomingDeviceToken} 取自 Cookie（信任凭据）。
 *
 * @param loginIp             客户端 IP（登录/短信审计日志）
 * @param userAgent           浏览器 UA（服务端取值，参与设备指纹；不信任前端传值）
 * @param incomingDeviceToken 请求携带的设备信任令牌明文（来自 HttpOnly Cookie；无则 null）
 */
public record AuthRequestContext(String loginIp, String userAgent, String incomingDeviceToken) {

    /** 空上下文（离线单测 / 无传输信息时使用） */
    public static AuthRequestContext empty() {
        return new AuthRequestContext(null, null, null);
    }
}
