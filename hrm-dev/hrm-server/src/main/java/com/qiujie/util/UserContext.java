package com.qiujie.util;

import com.qiujie.common.LoginUser;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由 JwtAuthFilter 请求开始时注入、请求结束时清理，业务层随时可取当前操作人。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
