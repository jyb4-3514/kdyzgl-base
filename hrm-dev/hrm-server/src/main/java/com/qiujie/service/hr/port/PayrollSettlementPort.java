package com.qiujie.service.hr.port;

/**
 * 跨域端口：离职薪资结算单创建（人事域 → 财务域，ADR-03 断环）。
 * <p>
 * <b>为什么用端口（依赖倒置）而非直接依赖财务域</b>：架构 §2.2 要求 hr 与 finance 之间无环，
 * 若 hr 直接 import finance 的 Service 即形成 {@code hr ↔ finance} 双向依赖。人事域只依赖本端口接口，
 * 由财务域（P6）提供实现，依赖方向恒为 {@code finance → hr}（实现方依赖接口方），断环成立。
 * <p>
 * <b>与架构 §2.3「领域事件」的关系（需主智能体裁定，见交付摘要事实性纠正）</b>：§2.3 该行记「领域事件」，
 * 但 Mock 契约要求 SETTLEMENT 步骤<b>同步</b>回填 {@code settlementPayrollId/No/Amount}（离职详情页展示结算单），
 * 而 {@code @TransactionalEventListener(AFTER_COMMIT)} 为提交后异步执行，无法满足该同步语义。
 * 故本批以「跨域端口 + 服务层直调」实现同等的依赖倒置断环，并保持同步语义；
 * 事件作为补偿/重试的备选方案，标 {@code TODO(扩展)}。
 * <p>
 * <b>P6 接入点</b>：财务域实现本接口并注册为 Spring Bean（{@code @Component} 或 {@code @Bean}），
 * P6 未就绪时由 {@code HrPortConfig} 提供的降级 Bean 兜底（抛「财务域未就绪」业务异常）。
 */
public interface PayrollSettlementPort {

    /**
     * 创建离职结算单（草稿）。
     *
     * @param command 结算命令（员工 / 月份 / 来源流程 / 备注）
     * @return 结算单引用（id / 单号 / 金额）
     */
    PayrollSettlementRef createSettlement(PayrollSettlementCommand command);

    // TODO(扩展): P6 落地后接入真实结算单创建；如需异步补偿，可在财务域监听
    //   OffboardingSettlementRequired 领域事件（AFTER_COMMIT）重试，本端口保持同步主链路。
}
