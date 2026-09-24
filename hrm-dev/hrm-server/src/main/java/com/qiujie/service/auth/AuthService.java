package com.qiujie.service.auth;

import com.qiujie.dto.auth.ChangePasswordRequest;
import com.qiujie.dto.auth.DeviceVerifyRequest;
import com.qiujie.dto.auth.LoginRequest;
import com.qiujie.dto.auth.SmsLoginRequest;
import com.qiujie.dto.auth.SmsSendRequest;
import com.qiujie.service.auth.support.AuthRequestContext;
import com.qiujie.vo.auth.CaptchaVO;
import com.qiujie.vo.auth.LoginVO;
import com.qiujie.vo.auth.MeVO;
import com.qiujie.vo.auth.SmsSendVO;
import com.qiujie.vo.auth.TrustedDeviceVO;

import java.util.List;

/**
 * 认证服务：登录 / 退出 / 当前用户 / 修改本人密码（api.md 4.1），
 * 以及 M4 登录契约改造新增的短信通道与设备信任能力（multi-client-architecture §4.1.2 端点 A1/A2/B1–B2/C1–C2/D1）。
 * <p>
 * <b>契约兼容（硬约束）</b>：既有 4 端点的 URL / 入参 / 出参 / 错误码<b>不得变更</b>；
 * 新增能力一律以<b>可选字段</b>表达（老前端不传即行为不变）。
 * <p>
 * <b>不含 B3 {@code /auth/session/renew}</b>：用户裁定「3 天到期强制重新登录」，不做短信续期。
 */
public interface AuthService {

    /**
     * 账号登录（改造后）。
     * <p>
     * 会话按「端 + 设备」维度持有（ADR-MC-03）：同端同设备重登覆盖旧会话，跨端/跨设备并存。
     * 端类型/设备标识取自请求体（新增可选字段）优先、请求头回退，仅作会话维度与审计，<b>不参与鉴权</b>；
     * 端准入（1110）是产品/审计约束，安全边界恒为「角色 + 数据范围」。
     *
     * @param ctx                传输层上下文（IP / UA / 请求携带的设备令牌）
     * @param headerClientType   既有 {@code X-Client-Type} 头（回退用；不改变改造前行为）
     * @param headerDeviceId     既有 {@code X-Device-Id} 头（回退用）
     * @return 登录出参；命中「未受信设备」时返回 {@code needDeviceVerify=true} + {@code twoFactorTicket}（无 token）
     */
    LoginVO login(LoginRequest request, AuthRequestContext ctx, String headerClientType, String headerDeviceId);

    /** 退出登录：删除<b>当前会话</b>（按 sid；旧 token 回退删旧键），幂等。不撤销设备信任（与既有语义一致） */
    void logout();

    /** 当前登录用户信息（手机号脱敏） */
    MeVO me();

    /** 修改本人密码：成功后删除该员工<b>全部会话</b>并<b>使其已信任设备全部失效</b>（安全要求「改密即失效」） */
    void changePassword(ChangePasswordRequest request);

    // ==================== M4 新增：短信通道与设备信任 ====================

    /** A1 短信验证码下发（公开；按场景 LOGIN / DEVICE_VERIFY / PERIODIC_REAUTH 分流） */
    SmsSendVO sendSms(SmsSendRequest request, AuthRequestContext ctx);

    /** A2 短信验证码登录（公开；短信本身即第二因子，登录即视为设备受信） */
    LoginVO smsLogin(SmsLoginRequest request, AuthRequestContext ctx);

    /** B2 新设备短信二次验证（公开；校验票据 + 验证码后签发会话与设备信任令牌） */
    LoginVO deviceVerify(DeviceVerifyRequest request, AuthRequestContext ctx);

    /** C1 本人受信设备列表（仅本人；IP 脱敏） */
    List<TrustedDeviceVO> listDevices();

    /** C2 撤销本人受信设备（仅本人；撤销当前设备等价于登出） */
    void revokeDevice(String deviceId);

    /** D1 图形验证码（公开；captcha 关闭时前端不调用） */
    CaptchaVO captcha();
}
