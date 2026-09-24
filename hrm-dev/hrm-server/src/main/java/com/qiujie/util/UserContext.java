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

    /**
     * 当前会话标识（多端会话：sid = JWT jti；M1 新增）。
     * 登出按 sid 精确注销当前会话，不影响同一员工其他端/设备会话。
     */
    public static String getJti() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getJti();
    }

    /**
     * 当前用户归属驿站 id（字符串形式，C-02）。
     * 返回 {@code null} 或空串表示无归属；非 ADMIN 无归属时数据范围收敛为「无数据」（架构 8-2 推荐 B）。
     */
    public static String getStationId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getStationId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
