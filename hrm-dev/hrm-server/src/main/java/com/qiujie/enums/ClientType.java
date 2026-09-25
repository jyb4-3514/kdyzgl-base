package com.qiujie.enums;

import java.util.Arrays;
import java.util.Locale;

/**
 * 客户端端类型（{@code X-Client-Type} 请求头 / 登录请求体 {@code clientType}，架构 multi-client-architecture §2.1.3）。
 * <p>
 * <b>取值域（唯一真源）</b>：{@code ADMIN / BOSS / STAFF / WEB}，三端语义与目标准入矩阵一致：
 * <ul>
 *   <li>{@link #ADMIN}=PC 管理端（pc.html）；</li>
 *   <li>{@link #BOSS}=管理端 H5（驿站精灵，{@code ?as=boss}）；</li>
 *   <li>{@link #STAFF}=员工端 H5（驿站助手，{@code ?as=station}）；</li>
 *   <li>{@link #WEB}=旧网页端 / 缺省值（历史值，按 PC 口径约束）。</li>
 * </ul>
 * <p>
 * <b>安全定位（必读）</b>：端类型是<b>客户端自称</b>的元数据，<b>可被伪造或省略</b>，故<b>不是鉴权 / 授权边界</b>——
 * 权限恒以服务端持有的会话角色（{@code LoginUser.role}）为唯一权威。但端类型<b>参与登录态的「端准入」约束</b>
 * （{@link com.qiujie.service.auth.support.ClientAdmissionPolicy}）：角色与端不匹配即拒登（1110），
 * 未知 / 缺省端 fail-closed。本枚举即端类型的唯一真源，不在别处再定义第二套取值域。
 * <p>
 * <b>为什么不回落缺省值（本次收紧根因）</b>：旧实现把缺失 / 未知值静默回落 {@code WEB}，使「不报端类型」既不报错、
 * 又在回退策略下「不受约束」，形成可被静默绕过的口子（见 {@code security-client-admission-review.md} 必改 2）。
 * 现改为严格归一：缺失 / 未知返回 {@code null}，由策略层按 fail-closed 拒绝，<b>不再回落 {@link #WEB}</b>。
 */
public enum ClientType {

    /** PC 管理端（pc.html，仅 ADMIN 可登） */
    ADMIN,
    /** 管理端 H5（驿站精灵，{@code ?as=boss}，仅 ADMIN 可登） */
    BOSS,
    /** 员工端 H5（驿站助手，STAFF + STATION_ADMIN 可登） */
    STAFF,
    /** 旧网页端 / 缺省值（历史值，按 PC 口径约束） */
    WEB;

    /**
     * 严格归一：忽略大小写与前后空白，命中返回对应枚举；缺失 / 空白 / 未知一律返回 {@code null}。
     * <p>
     * <b>注意</b>：{@code null} 不是「缺省端」而是「不可判定端」——调用方<b>不得</b>当作 {@link #WEB} 或据此放行，
     * 须按 fail-closed 处理（见 {@code ClientAdmissionPolicy#isLoginAllowed}）。历史前端上报的 {@code H5} 不属于本
     * 取值域，由策略层结合入口 {@code as} 派生为 {@link #BOSS} / {@link #STAFF}。
     */
    public static ClientType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.name().equals(upper)).findFirst().orElse(null);
    }

    /** 是否为已知端类型（大小写不敏感）；等价于 {@code parse(value) != null} */
    public static boolean isValid(String value) {
        return parse(value) != null;
    }
}
