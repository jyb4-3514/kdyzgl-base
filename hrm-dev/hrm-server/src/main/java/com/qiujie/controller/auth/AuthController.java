package com.qiujie.controller.auth;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.auth.ChangePasswordRequest;
import com.qiujie.dto.auth.DeviceVerifyRequest;
import com.qiujie.dto.auth.LoginRequest;
import com.qiujie.dto.auth.SmsLoginRequest;
import com.qiujie.dto.auth.SmsSendRequest;
import com.qiujie.service.auth.AuthService;
import com.qiujie.service.auth.support.AuthRequestContext;
import com.qiujie.service.auth.support.DeviceCookieSupport;
import com.qiujie.util.IpUtil;
import com.qiujie.vo.auth.CaptchaVO;
import com.qiujie.vo.auth.LoginVO;
import com.qiujie.vo.auth.MeVO;
import com.qiujie.vo.auth.SmsSendVO;
import com.qiujie.vo.auth.TrustedDeviceVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口（api.md 4.1 + M4 登录契约改造 multi-client-architecture §4.1.2）。
 * <p>
 * 公开白名单（{@link com.qiujie.common.PublicEndpoints}）：{@code /auth/login}、{@code /auth/sms/send}、
 * {@code /auth/sms/login}、{@code /auth/device/verify}、{@code /auth/captcha}；
 * 需登录：{@code /auth/logout}、{@code /auth/me}、{@code /auth/password}、{@code /auth/devices*}。
 * <p>
 * 注意：本控制器方法一律显式声明角色门槛（或在白名单内），避免「漏声明即静默放行」
 * （拦截器对非公开端点 fail-closed）。
 * <p>
 * <b>设备信任令牌</b>仅经 HttpOnly + Secure Cookie 下发（{@link DeviceCookieSupport}），
 * 不出现在任何响应体（{@code LoginVO.deviceToken} 已 {@code @JsonIgnore}）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DeviceCookieSupport deviceCookieSupport;

    /** 4.1.1 登录（公开）；M4 追加可选入参 clientType/as/device 与可选出参 deviceTrusted/needDeviceVerify 等 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // IP 与 UA 取自请求头（Nginx 反代透传 X-Forwarded-For），写入登录日志；
        // X-Client-Type 为端类型新契约（取值 ADMIN/BOSS/STAFF；非空优先于请求体 clientType，缺省/非法即 1110）；
        // X-Device-Id 为设备标识回退头。两者均不参与鉴权（权限恒以会话 role 为准）。
        LoginVO vo = authService.login(request, toContext(httpRequest),
                httpRequest.getHeader("X-Client-Type"), httpRequest.getHeader("X-Device-Id"));
        writeDeviceCookieIfPresent(httpResponse, httpRequest, vo);
        return Result.ok(vo);
    }

    /** 4.1.2 退出登录（幂等） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /** 4.1.3 当前用户信息 */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/me")
    public Result<MeVO> me() {
        return Result.ok(authService.me());
    }

    /** 4.1.4 修改本人密码（成功后需重新登录，并使其已信任设备失效） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }

    // ==================== M4 新增端点（对齐 Mock routes/auth.js） ====================

    /** A1 短信验证码下发（公开；按 scene 分流 LOGIN / DEVICE_VERIFY / PERIODIC_REAUTH） */
    @PostMapping("/sms/send")
    public Result<SmsSendVO> sendSms(@RequestBody SmsSendRequest request, HttpServletRequest httpRequest) {
        return Result.ok(authService.sendSms(request, toContext(httpRequest)));
    }

    /** A2 短信验证码登录（公开） */
    @PostMapping("/sms/login")
    public Result<LoginVO> smsLogin(@RequestBody SmsLoginRequest request,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // 端准入与密码路径同源：X-Client-Type 头优先、请求体 clientType 回退（缺省/非法即 1110）
        LoginVO vo = authService.smsLogin(request, toContext(httpRequest), httpRequest.getHeader("X-Client-Type"));
        writeDeviceCookieIfPresent(httpResponse, httpRequest, vo);
        return Result.ok(vo);
    }

    /** B2 新设备短信二次验证（公开；通过后签发会话与设备信任令牌） */
    @PostMapping("/device/verify")
    public Result<LoginVO> deviceVerify(@RequestBody DeviceVerifyRequest request,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginVO vo = authService.deviceVerify(request, toContext(httpRequest));
        writeDeviceCookieIfPresent(httpResponse, httpRequest, vo);
        return Result.ok(vo);
    }

    /** C1 本人受信设备列表（需登录；仅本人，IP 脱敏） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/devices")
    public Result<List<TrustedDeviceVO>> listDevices() {
        return Result.ok(authService.listDevices());
    }

    /** C2 撤销本人受信设备（需登录；越权受限——归属由服务端会话 employeeId 决定，路径参数仅作目标设备） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @DeleteMapping("/devices/{deviceId}")
    public Result<Void> revokeDevice(@PathVariable String deviceId) {
        authService.revokeDevice(deviceId);
        return Result.ok();
    }

    /** D1 图形验证码（公开；captcha 关闭时前端不调用） */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(authService.captcha());
    }

    // ==================== 内部 ====================

    /** 采集传输层上下文（IP / UA / 请求携带的设备令牌 Cookie） */
    private AuthRequestContext toContext(HttpServletRequest request) {
        return new AuthRequestContext(IpUtil.getClientIp(request), request.getHeader("User-Agent"),
                deviceCookieSupport.read(request));
    }

    /** 有新签发的设备令牌时写 Cookie（明文只在响应头出现一次；不进响应体） */
    private void writeDeviceCookieIfPresent(HttpServletResponse response, HttpServletRequest request, LoginVO vo) {
        if (vo != null && vo.getDeviceToken() != null && !vo.getDeviceToken().isBlank()) {
            deviceCookieSupport.write(response, request, vo.getDeviceToken());
        }
    }
}
