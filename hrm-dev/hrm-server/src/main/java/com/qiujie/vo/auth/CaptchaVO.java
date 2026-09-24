package com.qiujie.vo.auth;

import lombok.Data;

/**
 * D1 图形验证码响应（对齐前端 Mock {@code captcha} 返回体）。
 * <p>
 * <b>不返回图形码明文</b>：只返回票据与图片，明文仅存服务端 Redis。
 */
@Data
public class CaptchaVO {

    /** 图形验证码票据（A1 发码时回传校验） */
    private String ticket;

    /** 图片（Base64，不含 data URI 前缀） */
    private String imageBase64;

    /** 有效期（秒；对齐 hrm.auth.captcha-ttl-seconds，Mock 固定 120） */
    private Integer expireIn;
}
