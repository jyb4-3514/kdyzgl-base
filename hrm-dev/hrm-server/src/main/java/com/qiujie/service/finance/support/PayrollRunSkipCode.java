package com.qiujie.service.finance.support;

/**
 * 自动算薪跳过码（机器可读；db.md §8.6.7，V21 已移除 {@code EXHAUSTED}）。
 * <p>
 * 仅「已决定执行但被业务正常拦截」记 {@code SKIPPED}；不重试（终止类）。
 * 「未启用 / 未到点 / 当日已尝试 / 已占位」不产生运行记录（属「未进入执行」）。
 */
public final class PayrollRunSkipCode {

    /** 命中生成层 9405（该驿站该账期已有非可覆盖单） */
    public static final String BLOCKED_9405 = "BLOCKED_9405";
    /** 该驿站算薪配置非法（算薪日越界 / 时间格式非法） */
    public static final String CONFIG_INVALID = "CONFIG_INVALID";
    /** 存在人工 DRAFT 单，自动路径保护性跳过（算法 §1.5.2 (a)，避免覆盖重建与手工录入并发） */
    public static final String DRAFT_PROTECTED = "DRAFT_PROTECTED";

    private PayrollRunSkipCode() {
    }
}
