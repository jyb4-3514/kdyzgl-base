package com.qiujie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证与会话强类型配置（{@code hrm.auth.*}，架构 multi-client-architecture §3.5 / §4.3）。
 * <p>
 * 为什么外置：会话时效、并发上限、重认证周期属安全与工程超参，须可配可审计；代码内严禁内联常量
 * （规则 §11.4、反模式 A03）。业务口径（是否允许滑动续期、到期路径等）属待用户裁定项（架构 §6），
 * 本类仅承载配置，<b>不实现</b> M4 的登录契约与周期重认证状态机。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.auth")
public class AuthProperties {

    /**
     * 会话有效期（秒）。默认 259200 = 3 天（用户口径「每 3 天短信验证一次」）。
     * 取值范围：正整数。取值为 Redis 会话 TTL；与 JWT {@code exp}（{@code jwt.expire}）双控，
     * <b>取较短者生效</b>。M4 已把 {@code jwt.expire} 同步为 259200，两者一致。
     */
    private long sessionTtlSeconds = 259200L;

    /**
     * 单员工最大并发会话数（按端 + 设备维度计数）。默认 5。
     * 取值范围：正整数；{@code <= 0} 视为不限（不推荐）。
     * 超限处置：淘汰最旧会话（{@link com.qiujie.util.SessionEvictionPolicy}）。
     */
    private int maxSessionsPerEmployee = 5;

    /**
     * 是否启用设备信任（新设备短信二次验证）。默认 true。
     * 本批仅承载配置；设备信任表与判定逻辑属 M3/M4。
     */
    private boolean deviceTrustEnabled = true;

    /**
     * 设备信任有效期（秒）。默认 2592000 = 30 天（security-auth-review §4.2 建议）。
     * 取值范围：正整数。本批仅承载配置（M3 建表后生效）。
     */
    private long deviceTrustTtlSeconds = 2592000L;

    /**
     * 周期重认证周期（秒）。默认 259200 = 3 天。
     * 取值范围：正整数。本批仅承载配置；重认证状态机属 M4。
     */
    private long periodicReauthSeconds = 259200L;

    /**
     * 会话到期宽限（秒）。默认 300 = 5 分钟。
     * <p>
     * <b>不延长可用期</b>，只让服务端在「重认证窗口已过」时仍能读懂该请求，从而返回专用码
     * {@link com.qiujie.enums.ErrorCode#SESSION_EXPIRED}（1108）而不是裸 401：
     * <ul>
     *   <li>刷新/登出等路径会使 Redis 会话键与 JWT 的到期时刻出现毫秒级先后（JWT 通常先到期）；</li>
     *   <li>若无本宽限，超窗请求会在 JWT 解析阶段即抛过期异常 → 401，前端拿不到 1108 的「登录已到期」告知；</li>
     *   <li>鉴权窗口恒为 {@link #periodicReauthSeconds}：超窗一律 1108 拒登，宽限期内不存在任何「可用」请求。</li>
     * </ul>
     * 取值范围：非负整数；{@code 0} = 关闭宽限（超窗即 401，不建议）。
     */
    private long expiryGraceSeconds = 300L;

    /**
     * 短信通道是否配图形验证码。默认 false（对齐架构 §6 Q3「默认关闭，可配置开启」）。
     * 开启时 A1 发码要求携带有效 {@code captchaTicket}/{@code captchaCode}，否则 1106。
     */
    private boolean captchaEnabled = false;

    /**
     * 图形验证码有效期（秒）。默认 120（对齐前端 Mock {@code captcha.expireIn}）。
     * 取值范围：正整数。
     */
    private int captchaTtlSeconds = 120;

    /**
     * 二次验证票据（{@code twoFactorTicket}）有效期（秒）。默认 300 = 5 分钟（架构 §4.3.2「短 TTL，如 5 分钟」）。
     * 取值范围：正整数。票据仅存 Redis，不入库、不入出参以外的地方。
     */
    private int deviceTicketTtlSeconds = 300;

    /** 单员工可信任设备数上限。默认 5；取值：正整数。超限时淘汰最久未活跃的受信设备（服务端权威，前端不可绕过） */
    private int maxTrustedDevicesPerEmployee = 5;

    /**
     * 设备指纹 HMAC 盐（<b>服务端秘密</b>）。
     * <p>
     * 仓库内 {@code application.yml} 只写 {@code change_me_*} 占位符，真实值由主智能体托管、落服务器外置
     * {@code application-prod.yml}；<b>永不入库 / 进日志 / 进对话</b>。生产 profile 下若仍为占位/空 →
     * {@code DeviceFingerprint} 启动期 fail-fast（与 {@code SmsConfigGuard} 同口径，避免弱盐静默上线）。
     */
    private String deviceFingerprintSalt = "";

    /**
     * PC 管理端（{@link com.qiujie.enums.ClientType#ADMIN}）允许登录的角色集合。默认 {@code ["ADMIN"]}。
     * <p>
     * 取值：角色名集合，全 ASCII；YAML 可写标量逗号分隔形式（如 {@code ADMIN} 或 {@code ADMIN,STATION_ADMIN}），
     * 也可写 YAML 列表。逐项去空白、忽略大小写、非已知角色名丢弃——解析口径见
     * {@link com.qiujie.service.auth.support.ClientRolePolicy#normalizeAllowedRoles}。
     * <p>
     * 边界：显式配置为空集合 = 任何角色均不得从 PC 管理端登录（fail-closed）；键缺失则回落本默认值 ADMIN。
     * <p>
     * 为什么外置：端维度角色口径属产品 / 审计约束（security-auth-review §4.5、multi-client-architecture §3.6），
     * 须可配可审计；代码内严禁内联角色字面量判断（规则 §11.4、反模式 A03）。
     * 注：本键为单标量、全 ASCII，不受「YAML 非 ASCII 配置键被静默归并」问题影响（见 {@code application.yml} dispatch 注释）。
     */
    private List<String> pcAllowedRoles = new ArrayList<>(List.of("ADMIN"));
}
