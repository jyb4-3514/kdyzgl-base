package com.qiujie.service.finance.support;

/**
 * 驿站算薪配置变更动作（db.md / DDL: V21 {@code station_payroll_setting_log.action}）。
 * <p>
 * 每次保存必写一条；「启用 0→1」由 {@link #ENABLE} 行承载、可追溯（安全 M-9 硬要求）。
 */
public final class PayrollSettingLogAction {

    /** 首次创建（before 为 NULL） */
    public static final String CREATE = "CREATE";
    /** 字段变更（非启停翻转） */
    public static final String UPDATE = "UPDATE";
    /** 启用 0→1 */
    public static final String ENABLE = "ENABLE";
    /** 停用 1→0 */
    public static final String DISABLE = "DISABLE";

    private PayrollSettingLogAction() {
    }
}
