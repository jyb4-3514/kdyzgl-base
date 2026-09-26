package com.qiujie.service.support.sms;

/**
 * 短信场景（对齐架构 multi-client-architecture §4.1.2 / §4.4）。
 * <p>
 * 场景决定选择哪个阿里云模板 Code（{@code hrm.sms.aliyun.template-code-*}）与频控/校验口径；
 * 用于把「同一条短信通道」按业务语义隔离，便于审计与风控。
 */
public enum SmsScene {

    /** 短信登录（密码不可用时的备用登录通道） */
    LOGIN,
    /** 新设备登录二次验证（DEVICE_VERIFY） */
    DEVICE_VERIFY,
    /** 周期重认证（会话到期前以短信延续/重建会话） */
    PERIODIC_REAUTH,
    /**
     * 员工自助注册（公开提交前置验证，M-1）。
     * <p>
     * 与 LOGIN 的关键差异：<b>不判定手机号是否已注册</b>（注册者尚未成为员工），
     * 故不查 {@code employee}、不产生存在性差异；独立场景亦隔离模板与频控口径。
     */
    REGISTER;

    /** 是否为已知场景（防任意串进入通道调用） */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (SmsScene scene : values()) {
            if (scene.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
