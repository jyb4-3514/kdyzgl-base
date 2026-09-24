package com.qiujie.service.auth.support;

/**
 * 设备弱信号快照（登录时采集，用于二次验证通过后复算同一指纹）。
 * <p>
 * 为什么单独成记录类：设备指纹的输入必须「登录时」与「二次验证时」完全一致，否则幂等 upsert 失效、
 * 同一物理设备会生成多行受信记录。票据只存快照，不存指纹本身（指纹随盐轮换，存快照更稳）。
 * <p>
 * 全部字段均为客户端可伪造的弱信号，<b>不参与放行判定</b>。
 */
public record DeviceSnapshot(String deviceId, String platform, String model, String osVersion, String appVersion) {

    /** 空快照（无设备信息时使用；指纹入参全空，仍得确定值） */
    public static DeviceSnapshot empty() {
        return new DeviceSnapshot(null, null, null, null, null);
    }
}
