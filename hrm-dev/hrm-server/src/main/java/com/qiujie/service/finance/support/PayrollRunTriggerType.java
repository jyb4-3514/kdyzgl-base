package com.qiujie.service.finance.support;

/**
 * 自动算薪触发方式（db.md §8.6.7 / DDL: V20 {@code payroll_run.trigger_type}）。
 * <p>
 * {@code AUTO}=与 {@code dueAt} 同日正常到点；{@code CATCH_UP}=晚于 {@code dueAt} 所在日（日粒度补跑）；
 * {@code MANUAL}=管理员手工触发（I-4）。仅影响可观测性标签，不改变执行语义（算法 v1.3 §1.4/§1.3）。
 */
public enum PayrollRunTriggerType {

    AUTO,
    CATCH_UP,
    MANUAL;

    public static boolean isValid(String code) {
        for (PayrollRunTriggerType type : values()) {
            if (type.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
