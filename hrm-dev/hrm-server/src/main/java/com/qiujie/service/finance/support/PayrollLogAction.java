package com.qiujie.service.finance.support;

/**
 * 工资单留痕动作常量（对齐 db.md §8.6.6 {@code payroll_log.action} 枚举）。
 * <p>
 * 为什么集中成常量：动作名同时出现在写入侧（各写入口）与查询侧（I-7 时间线），散落字面量一旦拼错，
 * 审计检索会静默漏行；单点声明便于检索与对齐契约。
 */
public final class PayrollLogAction {

    /** 自动调度生成（B3 调度器；本批未接入） */
    public static final String GENERATE_AUTO = "GENERATE_AUTO";
    /** 手工触发生成（含覆盖重建） */
    public static final String GENERATE_MANUAL = "GENERATE_MANUAL";
    /**
     * 自动提交审核未执行（B4a 修补）：自动算薪生成后逐单自动 submit，失败单保持 {@code DRAFT} 并留痕。
     * <p>为什么单列动作：失败单并未真正提交，用 {@code SUBMIT} 记录会与事实不符（审计时间线会误读为已提交）；
     * 单列动作使「为何该单仍是草稿」在留痕中可判定。
     * <p>{@code TODO(扩展)}：本动作值需由数据库工程师同步 `db.md` §8.6.6 的 {@code action} 取值清单（列型 VARCHAR(32) 无需 DDL）。
     */
    public static final String AUTO_SUBMIT_SKIPPED = "AUTO_SUBMIT_SKIPPED";
    /** 新增一笔手工加/扣款 */
    public static final String ITEM_ADD = "ITEM_ADD";
    /** 修改既有 MANUAL 项金额 */
    public static final String ITEM_UPDATE = "ITEM_UPDATE";
    public static final String SUBMIT = "SUBMIT";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String PUBLISH = "PUBLISH";
    /** 异议退回后的再发布 */
    public static final String REPUBLISH = "REPUBLISH";
    public static final String CONFIRM = "CONFIRM";
    public static final String OBJECTION = "OBJECTION";
    /** 确认发放归档 */
    public static final String PAY = "PAY";

    /** 操作主体：人工 */
    public static final String OPERATOR_USER = "USER";
    /** 操作主体：自动调度 */
    public static final String OPERATOR_SYSTEM = "SYSTEM";

    private PayrollLogAction() {
    }
}
