package com.qiujie.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文数据（由 JwtAuthFilter 从 Redis 会话构建，经 UserContext 注入）。
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String role;
    private String jti;
}
