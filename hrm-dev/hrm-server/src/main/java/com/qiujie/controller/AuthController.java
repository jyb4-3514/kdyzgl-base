package com.qiujie.controller;

import com.qiujie.common.Result;
import com.qiujie.dto.ChangePasswordRequest;
import com.qiujie.dto.LoginRequest;
import com.qiujie.service.AuthService;
import com.qiujie.util.IpUtil;
import com.qiujie.vo.LoginVO;
import com.qiujie.vo.MeVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（api.md 4.1）：
 * login 为公开白名单；logout/me/password 需登录（任意角色）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 4.1.1 登录（公开） */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // IP 与 UA 取自请求头（Nginx 反代透传 X-Forwarded-For），写入登录日志
        return Result.ok(authService.login(request,
                IpUtil.getClientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    /** 4.1.2 退出登录（幂等） */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /** 4.1.3 当前用户信息 */
    @GetMapping("/me")
    public Result<MeVO> me() {
        return Result.ok(authService.me());
    }

    /** 4.1.4 修改本人密码（成功后需重新登录） */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }
}
