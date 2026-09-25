package com.qiujie.service.finance.support;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 计薪项来源（对齐 db.md §8.6.2 与 Mock {@code PAYROLL_ITEM_SOURCE_LABEL}）。
 * <p>
 * 四类来源各对应一个解析器实现，注册进 {@link PayrollResolverRegistry}；
 * <b>新增来源只需新增一个解析器实现，不改核心算薪代码</b>（算法 S2 注册表 / 开闭原则）。
 */
public enum PayrollSource {

    /** 人事定薪项（基本工资 / 岗位工资 / 绩效基数 / 津贴合计 / 指定津贴项） */
    FIXED("人事定薪项"),
    /** 考勤推算（迟到 / 早退 / 缺勤 / 异常卡 / 请假，按次计或达标发放） */
    ATTENDANCE("考勤推算"),
    /** KPI 考核（绩效基数 × KPI 系数） */
    KPI("KPI 考核"),
    /** 人工填写（草稿 / 驳回状态下可改） */
    MANUAL("人工填写"),
    /**
     * 出勤折算（S2b 班次制：定薪字段 × 实出班次 ÷ 应出班次）。
     * <p>
     * 为什么单独成源而非复用 FIXED：折算语义是「计薪方式」而非「取数」——FIXED 只取定薪原值，
     * 无法表达按出勤比例的金额；单列一个 source 才能把「折算」与「罚款」在明细上分开，避免可解释性混淆（方案 §4 候选 A）。
     * 命名受 {@code payroll_rule_item.source VARCHAR(16)} 列宽约束，故取 {@code PRORATED}（8 字符），不用 ATTENDANCE_PRORATED。
     */
    PRORATED("出勤折算");

    private final String label;

    PayrollSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 已知来源集合（规则项校验用，顺序稳定） */
    public static Set<String> codes() {
        Set<String> codes = new LinkedHashSet<>();
        for (PayrollSource source : values()) {
            codes.add(source.name());
        }
        return codes;
    }

    /** 是否为已知来源 */
    public static boolean isValid(String code) {
        return codes().contains(code);
    }

    /** 取中文标签；未知来源回退原值（对齐 Mock {@code ... || code}） */
    public static String labelOf(String code) {
        for (PayrollSource source : values()) {
            if (source.name().equals(code)) {
                return source.label;
            }
        }
        return code;
    }
}
