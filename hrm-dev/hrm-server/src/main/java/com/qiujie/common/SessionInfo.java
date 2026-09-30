package com.qiujie.common;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 登录会话数据（架构 multi-client-architecture §2.3.2）。
 * <p>
 * Key 规范（多端会话，ADR-MC-03）：
 * <ul>
 *   <li>会话主体：{@code hrm:session:{sid}}（新 token；sid = JWT {@code jti}）；</li>
 *   <li>员工索引：{@code hrm:session:idx:{employeeId}}（Set&lt;sid&gt;，用于强制下线全部会话）；</li>
 *   <li>旧键：{@code hrm:session:{employeeId}}（兼容期只读，新登不再写，见 {@code JwtAuthFilter} 回退分支）。</li>
 * </ul>
 * <b>序列化兼容</b>：字段全部使用 String，避免 JSON 序号兼容问题；本次仅<b>增字段</b>（不减不改），
 * Jackson 反序列化旧会话 JSON 时新增字段为 {@code null}，<b>不抛错</b>，存量旧会话可直接读取。
 * <p>
 * 为什么保留 6 参构造器：既有调用方（登录写会话）按原签名编译，避免升级期连带改动。
 */
@Data
@NoArgsConstructor
public class SessionInfo {

    private String jti;
    private String username;
    private String role;
    private String loginIp;
    /** 登录时间（yyyy-MM-dd HH:mm:ss） */
    private String loginTime;

    /**
     * 归属驿站 id（C-02 新增，供非 ADMIN 数据范围收敛取用）。三态语义：
     * <ul>
     *   <li>{@code null}：本次升级前写入的旧会话尚无该字段；由 JwtAuthFilter 回查员工表补齐并回写；</li>
     *   <li>{@code ""}：已解析且该用户确无归属驿站（与 null 区分，避免每次请求都回查数据库）；</li>
     *   <li>其它：驿站 id 字符串（如 "3"）。</li>
     * </ul>
     * 为什么用 String：沿用类头约定，避免 JSON 序号兼容问题（架构 C-02）。
     */
    private String stationId;

    // ==================== M1 会话多端化新增字段（只增不减，旧会话反序列化兼容） ====================

    /**
     * 会话标识 sid（= JWT {@code jti}，64 位十六进制高熵串）。
     * 冗余存储于会话体内，便于按 sid 直读校验与单会话吊销时定位归属员工。
     * 旧会话该字段为 {@code null}。
     */
    private String sid;

    /** 归属员工 id（字符串形式，仅用于索引键 {@code hrm:session:idx:{employeeId}} 的组装，避免 JSON 数字类型歧义） */
    private String employeeId;

    /**
     * 端类型（{@link com.qiujie.enums.ClientType} 归一后的名称，如 WEB/STAFF/BOSS/ADMIN）。
     * <b>不可信元数据</b>，仅用于审计/会话维度/限流；权限恒以 {@link #role}（服务端权威）为准。
     * 旧会话该字段为 {@code null}（视作未知端，互踢时与空串等价）。
     */
    private String clientType;

    /** 设备标识（前端上报，可空；空视为未知设备）。同样仅作审计/互踢维度，不作安全依据 */
    private String deviceId;

    /**
     * 本次认证时刻（服务端单调时钟，epoch 秒；M4 新增，即安全报告所称 {@code lastAuthAt}）。
     * <p>
     * 服务端权威的「重认证窗口」判定依据：请求时若 {@code now - loginEpochSeconds >=
     * hrm.auth.periodic-reauth-seconds}（默认 3 天）→ 返回 1108（到期强制重登录）。
     * <b>绝不取客户端时间</b>（可被改系统时间绕过）；旧会话该字段为 {@code null}（不参与窗口判定，保持改造前行为）。
     */
    private Long loginEpochSeconds;

    /**
     * 是否已完成首登改密（ARCH-C-7 / 主代理裁定 A-⑤，登录时从 {@code employee.pwd_changed} 快照）。
     * <p>
     * 三态语义：{@code false}=未改密（业务接口被 {@code PwdChangedInterceptor} 拦截）；{@code true}=已改密；
     * {@code null}=本次升级前写入的旧会话，由 {@code JwtAuthFilter} 回查员工表补齐并回写（与 stationId 同自愈口径）。
     * <p>
     * 为什么随会话快照：避免每请求回表；改密接口成功后删除全部会话，用户须重登，新会话即携带权威值。
     */
    private Boolean pwdChanged;

    /**
     * 兼容构造器（原 6 参签名，保持既有编译兼容）。
     * 新增字段（sid/employeeId/clientType/deviceId）由调用方经 setter 补齐。
     */
    public SessionInfo(String jti, String username, String role,
                       String loginIp, String loginTime, String stationId) {
        this.jti = jti;
        this.username = username;
        this.role = role;
        this.loginIp = loginIp;
        this.loginTime = loginTime;
        this.stationId = stationId;
    }
}
