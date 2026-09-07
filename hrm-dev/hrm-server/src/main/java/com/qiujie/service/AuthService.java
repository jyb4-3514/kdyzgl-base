package com.qiujie.service;

import com.qiujie.dto.ChangePasswordRequest;
import com.qiujie.dto.LoginRequest;
import com.qiujie.vo.LoginVO;
import com.qiujie.vo.MeVO;

/**
 * 认证服务：登录 / 退出 / 当前用户 / 修改本人密码（api.md 4.1）。
 */
public interface AuthService {

    /**
     * 账号登录。
     *
     * @param loginIp   客户端 IP（登录日志）
     * @param userAgent 浏览器 UA（登录日志，截断至 255）
     */
    LoginVO login(LoginRequest request, String loginIp, String userAgent);

    /** 退出登录：删除本人会话（幂等） */
    void logout();

    /** 当前登录用户信息（手机号脱敏） */
    MeVO me();

    /** 修改本人密码：成功后删除会话强制重新登录 */
    void changePassword(ChangePasswordRequest request);
}
