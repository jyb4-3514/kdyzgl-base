package com.qiujie.common;

import java.util.Set;

/**
 * 公开端点白名单（对齐 Mock 中 {@code auth:false} 的路由）。
 * <p>
 * 单一真源：{@code JwtAuthFilter}（免认证放行）与 {@code RequireRolesInterceptor}（免角色门槛）共用本清单，
 * 避免两处各写一份导致漂移（漏放行或误放行）。
 * <p>
 * 各条路径互不重名，故按路径匹配即可；若后续出现「同一路径不同方法」的部分公开端点，需改为「方法 + 路径」匹配。
 * <p>
 * <b>安全要求</b>（security-auth-review §5-R9）：新增任一公开端点均扩大外部攻击面（枚举 / 刷量 / 短信轰炸），
 * 公网暴露前须再过 P0.5 安全评估。
 */
public final class PublicEndpoints {

    /** 登录（api.md 4.1.1） */
    private static final String AUTH_LOGIN = "/api/v1/auth/login";

    /**
     * 企微群消息自动派单（预留，P8 实现；本批仅白名单就位）。
     * TODO(扩展): 企微回调签名/解密（msg_signature / EncodingAESKey）落地前不得公网暴露，
     *   须先经网络安全工程师评估、主智能体授权（架构 8-3 / R-3）。
     */
    private static final String WORK_ORDER_AUTO_DISPATCH = "/api/v1/work-orders/auto-dispatch";

    // ==================== M4 登录契约改造新增公开端点（对齐 Mock routes/auth.js 的 auth:false 路由） ====================

    /** A1 短信验证码下发（multi-client-architecture §4.1.2） */
    private static final String AUTH_SMS_SEND = "/api/v1/auth/sms/send";
    /** A2 短信验证码登录 */
    private static final String AUTH_SMS_LOGIN = "/api/v1/auth/sms/login";
    /** B2 新设备短信二次验证 */
    private static final String AUTH_DEVICE_VERIFY = "/api/v1/auth/device/verify";
    /** D1 图形验证码（可选；captcha 开启时使用） */
    private static final String AUTH_CAPTCHA = "/api/v1/auth/captcha";

    // ==================== B3 员工自助注册新增公开端点（registration-design §3.1，M-6） ====================

    /**
     * 提交注册申请（R-2，**唯一净新增公开端点**）。
     * <p>
     * 定稿（U-04/U-06）：公开面仅 R-1（复用 {@code /auth/sms/send}，加 {@code scene=REGISTER}）与 R-2 两条；
     * R-3 转 ADMIN-only、R-4 取消、R-5 取消公开 → 本清单<b>净新增 1 条</b>。
     * <p>
     * 安全要求：扩大外部攻击面（刷单/枚举），公网暴露前须过 P0.5 安全评估（security-registration-review M-1~M-9）。
     */
    private static final String REGISTRATION_SUBMIT = "/api/v1/registration";

    /**
     * 公开端点集合。
     * <p>
     * 注：{@code /api/v1/auth/devices}（C1）与 {@code /api/v1/auth/devices/{deviceId}}（C2）
     * <b>不在</b>白名单——须登录态且仅限本人设备；{@code /api/v1/registration/{applyNo}}（R-3）亦不在白名单（ADMIN-only）。
     */
    private static final Set<String> PATHS = Set.of(
            AUTH_LOGIN, WORK_ORDER_AUTO_DISPATCH,
            AUTH_SMS_SEND, AUTH_SMS_LOGIN, AUTH_DEVICE_VERIFY, AUTH_CAPTCHA,
            REGISTRATION_SUBMIT);

    private PublicEndpoints() {
    }

    /** 是否为公开端点（免认证 + 免角色门槛） */
    public static boolean isPublic(String path) {
        return path != null && PATHS.contains(path);
    }

    /** 只读快照，便于回归核对与单测断言 */
    public static Set<String> all() {
        return PATHS;
    }
}
