package com.qiujie.common;

import lombok.Data;

/**
 * 当前登录用户上下文数据（由 JwtAuthFilter 从 Redis 会话构建，经 UserContext 注入）。
 * <p>
 * 本次仅<b>增字段</b>（clientType/deviceId），保留原 5 参构造器以维持既有调用方编译兼容。
 */
@Data
public class LoginUser {

    private Long userId;
    private String username;
    private String role;
    private String jti;

    /**
     * 归属驿站 id（C-02 新增），语义同 {@link SessionInfo#getStationId()}：
     * {@code null}/空串 = 无归属；其它 = 驿站 id 字符串。
     * 非 ADMIN 的数据范围收敛（L1）与资源归属校验（L3）均取本值。
     */
    private String stationId;

    /**
     * 端类型（{@link com.qiujie.enums.ClientType} 归一值，M1 新增）。
     * <b>不可信元数据</b>：仅作审计/会话维度/限流，<b>不参与鉴权授权</b>；权限恒以 {@link #role} 为准。
     */
    private String clientType;

    /** 设备标识（M1 新增，可空）。仅作审计/会话维度，不作安全依据 */
    private String deviceId;

    /** 兼容构造器（原 5 参签名，保持既有调用方/Tests 编译兼容） */
    public LoginUser(Long userId, String username, String role, String jti, String stationId) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.jti = jti;
        this.stationId = stationId;
    }

    /** 全参构造器（含 M1 新增的端/设备维度字段） */
    public LoginUser(Long userId, String username, String role, String jti, String stationId,
                     String clientType, String deviceId) {
        this(userId, username, role, jti, stationId);
        this.clientType = clientType;
        this.deviceId = deviceId;
    }
}
