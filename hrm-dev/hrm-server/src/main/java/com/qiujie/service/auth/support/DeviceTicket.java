package com.qiujie.service.auth.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新设备二次验证票据载荷（Redis 暂存，不落库）。
 * <p>
 * 用途：密码登录命中「未受信设备」时下发 {@code twoFactorTicket}（短 TTL，见
 * {@code hrm.auth.device-ticket-ttl-seconds}），后续 {@code /auth/sms/send}(DEVICE_VERIFY) 与
 * {@code /auth/device/verify} 以票据定位员工与设备，<b>无需前端回传明文手机号</b>
 * （设备步手机号在页面侧为脱敏只读）。
 * <p>
 * 与前端 Mock {@code deviceTickets} 字段一致（employeeId / deviceId / clientType / entryAs / expireAt），
 * 并<b>额外快照设备弱信号</b>（platform/model/osVersion/appVersion）：二次验证通过时须以与登录时<b>相同</b>的
 * 指纹入参做幂等 upsert，否则会因指纹漂移在同一物理设备上生成重复受信行。
 * <p>
 * <b>票据本身即凭据</b>：高熵随机串、短 TTL、校验成功即删；不打印、不外泄。
 */
@Data
@NoArgsConstructor
public class DeviceTicket {

    /** 归属员工 id（票据唯一可定位到的身份，服务端权威） */
    private Long employeeId;

    /** 前端上报设备标识（弱信号，仅用于受信设备登记展示） */
    private String deviceId;

    /** 端平台（ANDROID/IOS/H5/WEB；弱信号快照） */
    private String platform;

    /** 设备型号（弱信号快照） */
    private String model;

    /** 系统版本（弱信号快照） */
    private String osVersion;

    /** 壳版本（弱信号快照） */
    private String appVersion;

    /** 端类型（{@code ClientType} 规范名 ADMIN/BOSS/STAFF/WEB；用于端准入复判与平台归类） */
    private String clientType;

    /** 入口视角参数（如 boss；用于管理端准入复判） */
    private String entryAs;

    /** 票据到期时刻（epoch 毫秒；服务端时钟） */
    private Long expireAtEpochMillis;

    /**
     * 是否已到期（{@code expireAt} 为空视为已到期，避免脏数据长期可用）。
     * <p>{@link JsonIgnore}：本方法为派生判定，不应成为 JSON 属性（避免 Redis 载荷多出只读字段）。
     */
    @JsonIgnore
    public boolean isExpired(long nowEpochMillis) {
        return expireAtEpochMillis == null || nowEpochMillis >= expireAtEpochMillis;
    }
}
