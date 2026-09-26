package com.qiujie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 员工自助注册强类型配置（{@code hrm.registration.*}，registration-design §1.3 / §3 / §11.2 M-8）。
 * <p>
 * <b>为什么外置</b>：申请失效天数、留存期、注册提交日上限均属「可调业务/安全参数」，
 * 内联即形成不可审计的魔法数（规则 §11.4、反模式 A03）；全部可外置、可运维热改。
 * <p>
 * 不含计费/提成/SLA/考核口径类参数（属口径红线，须用户裁定后才可落配置）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.registration")
public class RegistrationProperties {

    /**
     * 申请单失效天数（N=7 定稿，§1.3）：{@code expire_time = create_time + expireDays}；
     * 超期且仍 SUBMITTED 的申请按惰性判定转 EXPIRED（并同步 hr_flow → REJECTED）。
     */
    private int expireDays = 7;

    /**
     * 已拒绝/已失效数据的留存天数（U-14 定稿 N=30，M-8）：超期终态数据由清理任务移除，
     * 未过审数据不长期滞留。
     */
    private int retentionDays = 30;

    /**
     * 同手机号「注册提交」每日上限（次）（S-2/M-7）。默认 5；{@code <=0} 视为不限。
     * <p>
     * 与短信发码频控互补：即便验证码可发，也限制单号建单次数，阻断「无成本刷单」。
     * TODO(扩展): 具体上限值属运营/安全口径，若用户裁定调整须回到 config 与本注释同步。
     */
    private int submitDailyLimitPerPhone = 5;
}
