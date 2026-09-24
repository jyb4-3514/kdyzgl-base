package com.qiujie.dto.auth;

import lombok.Data;

/**
 * 登录设备信息（前端采集上报，全部为<b>可伪造的弱信号</b>）。
 * <p>
 * 与前端 {@code hrm-demo/src/shared/device.js#collectDevicePayload} 逐字段对齐；
 * 采集项清单见 multi-client-architecture §4.1.3。
 * <p>
 * <b>安全定位</b>：这些字段<b>绝不用于放行判定</b>——是否受信由服务端签发的 {@code device_token} 摘要决定。
 * 本对象仅用于：① 服务端 HMAC 设备指纹（幂等登记/审计）；② 设备列表展示。
 * {@code userAgent} 不在本对象内：UA 一律由服务端从请求头取，不信任前端传值。
 */
@Data
public class DeviceInfo {

    /** 设备稳定标识（前端生成并持久化；弱信号） */
    private String deviceId;

    /** 端平台：WEB / H5（H5 壳内可上报 ANDROID/IOS） */
    private String platform;

    /** 设备型号（弱信号） */
    private String model;

    /** 系统版本（弱信号） */
    private String osVersion;

    /** 壳版本（H5 为空；弱信号） */
    private String appVersion;

    /** 屏幕尺寸 `WxH`（弱信号，仅审计） */
    private String screen;

    /** 时区（弱信号，仅审计） */
    private String timezone;
}
