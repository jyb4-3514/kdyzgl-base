package com.qiujie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信通道强类型配置（{@code hrm.sms.*}，架构 multi-client-architecture §3.5 / §4.4）。
 * <p>
 * <b>凭据红线</b>：本类字段默认值为空串，仓库内 {@code application.yml} 只写 {@code change_me_*} 占位符；
 * 真实 AccessKey 由主智能体托管、落服务器外置 {@code application-prod.yml}，<b>永不入库/进日志/进对话</b>。
 * <p>
 * 频控与验证码长度/TTL/尝试上限一律走本配置（禁止硬编码，规则 §11.4）；
 * 频控的具体算法（令牌桶/滑动窗口）属算法工程师选型范畴（路由 R09），本批只承载维度与默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.sms")
public class SmsProperties {

    /**
     * 短信供应商：{@code aliyun}（生产）| {@code none}（降级：仅日志，不真正发送）。
     * 默认 {@code none}：保证仓库在<b>未配置任何 Key</b> 时可正常启动（降级通道）；
     * 生产环境若仍为 none 或 Key 无效 → 启动期 fail-fast（见 {@code SmsConfigGuard}）。
     */
    private String provider = "none";

    private Aliyun aliyun = new Aliyun();

    /**
     * 验证码长度（位）。默认 6；取值范围 [4, 8]（{@code SmsCodeGenerator} 会做防御性钳制）。
     */
    private int codeLength = 6;

    /**
     * 验证码有效期（秒）。默认 300 = 5 分钟；取值范围：正整数。
     */
    private int codeTtlSeconds = 300;

    /**
     * 同手机号最小发送间隔（秒）。默认 60；取值范围：正整数。
     */
    private int sendIntervalSeconds = 60;

    /**
     * 同手机号每日发送上限（次）。默认 10；取值范围：正整数。
     */
    private int dailyLimitPerPhone = 10;

    /**
     * 同 IP 每小时发送上限（次）。默认 20；取值范围：正整数；{@code <=0} 视为不限。
     * 维度键 {@code hrm:sms:limit:ip:*}（架构 §4.4.2 三维度频控之「同 IP」）。
     */
    private int ipHourlyLimit = 20;

    /**
     * 同设备每小时发送上限（次）。默认 10；取值范围：正整数；{@code <=0} 视为不限。
     * 设备标识为前端自称的弱信号，仅用于降低刷量成本，不作安全依据。
     */
    private int deviceHourlyLimit = 10;

    /**
     * 同账号（员工）每日发送上限（次）。默认 10；取值范围：正整数；{@code <=0} 视为不限。
     * 用于阻断「同一账号换手机号/换设备」绕过多维限频（security-auth-review §4.4 第 ③ 条「全维限频」）。
     */
    private int accountDailyLimit = 10;

    /**
     * 全局每日发送上限（次）。默认 500；取值范围：正整数；{@code <=0} 视为不限。
     * <p>
     * M-7/S-2：在既有四维（手机号 / IP / 设备 / 账号）之外叠加的<b>成本兜底</b>——即使攻击者持续换号、换 IP、换设备，
     * 也无法突破当日总发送量。键 {@code hrm:sms:limit:global}（1 天 TTL，惰性重置）。
     */
    private int globalDailyLimit = 500;

    /**
     * 单次验证码最大校验尝试次数。默认 5；取值范围：正整数。
     * 达上限即作废验证码（防暴力猜测）。
     */
    private int maxVerifyAttempts = 5;

    /**
     * 降级通道（{@code provider=none}，即未配置厂商凭据）使用的开发固定验证码。默认 {@code 000000}。
     * <p>
     * <b>仅非生产生效</b>：生产 profile 下 {@code provider} 必须为 {@code aliyun} 且凭据齐备，
     * 否则 {@code SmsConfigGuard} 启动期 fail-fast——故本值不可能在生产被使用。
     * 与前端演示态固定码（{@code hrm-demo} 演示资产）保持一致，便于联调核对。
     * <p>
     * 空串 = 不启用固定码（降级通道下改为随机码，但此时无人能取到码，联调不便）。
     */
    private String devFixedCode = "000000";

    /**
     * 测试环境「万能验证码」。默认 <b>空串 = 关闭</b>（不改变任何既有行为）。
     * <p>
     * <b>语义</b>：空串关闭；非空时，若<b>同时</b>满足「非生产环境」且「请求提交的 code 与该值恒等」，
     * 则在 {@code /auth/device/verify}、{@code /auth/sms/login} 两处校验入口<b>直接判定通过</b>——
     * 跳过 Redis 取码与比对，也<b>不消耗</b>任何验证码或尝试计数；但设备信任签发、会话建立、
     * 端准入（1110）、账号状态（1002）等授权判定与登录链路<b>一律照常</b>。
     * <p>
     * 与 {@link #devFixedCode} 的区别：{@code dev-fixed-code} 只决定「降级通道生成出来的验证码是什么」，
     * 仍必须<b>先发码</b>再比对；本键是「不用先发码也能过的放行码」。
     * <p>
     * <b>生产禁止</b>：prod profile 下本键非空即启动失败（{@code SmsConfigGuard} fail-fast）。
     * <b>红线</b>：该值绝不写入任何日志 / 响应体 / 异常信息（启动 WARN 只说明「已启用」，不回显码值）。
     */
    private String devUniversalCode = "";

    /** 阿里云短信通道参数（占位符，真实值由主智能体托管下发） */
    @Data
    public static class Aliyun {
        /** AccessKeyId（占位符） */
        private String accessKeyId = "";
        /** AccessKeySecret（占位符） */
        private String accessKeySecret = "";
        /** 签名名称 */
        private String signName = "";
        /** 登录场景模板 Code */
        private String templateCodeLogin = "";
        /** 新设备验证场景模板 Code */
        private String templateCodeDevice = "";
        /** 周期重认证场景模板 Code */
        private String templateCodeReauth = "";
        /** 员工自助注册场景模板 Code（M-1 新增场景 REGISTER） */
        private String templateCodeRegister = "";
    }
}
