package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 受信设备（登录体系改造，服务端持有信任态）。
 * <p>
 * 表结构真源：`db/migration/mysql/V15__auth_trusted_device.sql`（18 列 / 3 索引），
 * 设计依据：multi-client-architecture §4.2.1 表1、security-auth-review §4.2。
 * <p>
 * <b>安全要点</b>：
 * <ul>
 *   <li>{@code deviceTokenHash} 仅存服务端签发 {@code device_token} 的 SHA-256 摘要，<b>绝不存明文</b>；</li>
 *   <li>{@code deviceFingerprint} 为服务端 HMAC（盐外置），与 {@code employeeId} 组成唯一键，用于<b>幂等 upsert</b>；</li>
 *   <li>{@code deviceId/platform/model/osVersion/appVersion} 为客户端可伪造的<b>弱信号</b>，仅审计与展示；</li>
 *   <li>撤销用业务标志 {@code revoked}/{@code revokedAt}（软撤销，保留审计），<b>不使用 is_deleted</b>——
 *       本表不适用逻辑删除（撤销后可复用同一行重新受信，见 db.md §10.2 例外声明）。</li>
 * </ul>
 */
@Data
@TableName("auth_trusted_device")
public class AuthTrustedDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 服务端设备指纹摘要（HMAC-SHA256 hex；弱信号，仅幂等登记/审计，非放行依据） */
    private String deviceFingerprint;

    /** 服务端签发 device_token 的摘要（SHA-256 hex；仅存摘要，绝不存明文） */
    private String deviceTokenHash;

    /** 前端上报设备 ID（仅展示/排障，不作放行依据） */
    private String deviceId;

    /** 端平台：ANDROID/IOS/H5/WEB */
    private String platform;

    /** 设备型号（弱信号快照，仅审计） */
    private String model;

    /** 系统版本（弱信号快照，仅审计） */
    private String osVersion;

    /** 壳版本（H5 为空；弱信号快照，仅审计） */
    private String appVersion;

    /** 最近来源 IP（IPv4/IPv6；出参脱敏） */
    private String lastIp;

    /** 首次受信时间（信任建立时刻，重信不复位） */
    private LocalDateTime firstSeenTime;

    /** 最近活跃时间（每次成功校验刷新） */
    private LocalDateTime lastSeenTime;

    /** 信任有效期截止（到期须重新验证；null 由配置周期兜底，判定见 TrustedDevicePolicy） */
    private LocalDateTime expiresAt;

    /** 是否受信：0=否，1=是 */
    private Integer trusted;

    /** 是否已撤销：0=否，1=是（软撤销，保留审计） */
    private Integer revoked;

    /** 撤销时间（改密/强制下线/自助撤销时写入，审计） */
    private LocalDateTime revokedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
