package com.qiujie.vo.auth;

import lombok.Data;

/**
 * C1 受信设备列表项（对齐前端 Mock {@code listDevices} 返回体）。
 * <p>
 * <b>越权约束</b>：仅返回<b>本人</b>（会话 employeeId）设备；{@code lastIp} 一律脱敏。
 * 不返回 {@code device_token_hash} / {@code device_fingerprint} 等任何信任凭据或内部标识。
 */
@Data
public class TrustedDeviceVO {

    /** 前端设备标识（弱信号，仅展示） */
    private String deviceId;

    /** 端平台：ANDROID/IOS/H5/WEB */
    private String platform;

    /** 设备型号 */
    private String model;

    /** 最近来源 IP（末段打码脱敏，如 192.168.1.**） */
    private String lastIp;

    /** 最近活跃时间（yyyy-MM-dd HH:mm:ss） */
    private String lastSeenTime;

    /** 是否为当前会话所在设备（用于「本机」标识；前端据此决定撤销提示） */
    private Boolean current;
}
