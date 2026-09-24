package com.qiujie.service.finance.support;

import com.qiujie.dto.finance.PayrollRuleItemRequest;
import com.qiujie.dto.finance.PayrollRuleRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 计薪规则入参校验（纯逻辑，文案与 Mock {@code validateRuleBody / validateItems} 逐字一致）。
 * <p>
 * 为什么把校验单独抽出：规则项是「配置驱动算薪」的入口，脏配置（非法枚举 / 重复 key / 非法 params）
 * 一旦入库，算薪会在运行时才暴露；前置校验让错误在保存时就以 400 明确文案返回。
 * 返回首个错误文案，无错返回 {@code null}。
 */
public final class PayrollRuleValidator {

    /** 规则项 key：大写字母开头，仅大写字母/数字/下划线 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final String ITEM_TYPES = "ADDITION / DEDUCTION";
    private static final String ITEM_SOURCES = "FIXED / ATTENDANCE / KPI / MANUAL";

    private PayrollRuleValidator() {
    }

    /**
     * @param request  规则入参
     * @param isCreate true=新建（ruleName/items 必填）；false=编辑（仅校验显式传入项）
     * @return 首个错误文案；合法返回 null
     */
    public static String validate(PayrollRuleRequest request, boolean isCreate) {
        if (request == null) {
            return "请求体缺失";
        }
        if (isCreate || request.getRuleName() != null) {
            if (!textLen(request.getRuleName(), 2, 50)) {
                return "规则名称长度须为 2-50";
            }
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            return "status 仅支持 0 / 1";
        }
        if (request.getRemark() != null && !request.getRemark().isBlank() && !textLen(request.getRemark(), 0, 200)) {
            return "备注不可超过 200 字";
        }
        if (isCreate || request.getItems() != null) {
            return validateItems(request.getItems());
        }
        return null;
    }

    /** 规则项校验：非空数组、key 合法性/唯一、名称、类型、来源、params 对象、enabled 0/1 */
    public static String validateItems(List<PayrollRuleItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return "items 须为非空数组";
        }
        Set<String> keys = new HashSet<>();
        for (PayrollRuleItemRequest item : items) {
            if (item == null || !textLen(item.getKey(), 1, 30)) {
                return "规则项 key 长度须为 1-30";
            }
            String key = item.getKey().trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                return "规则项 key 须为大写字母、数字与下划线";
            }
            if (!keys.add(key)) {
                return "规则项 key 重复：" + key;
            }
            if (!textLen(item.getName(), 1, 20)) {
                return "规则项名称长度须为 1-20";
            }
            if (!PayrollItemType.isValid(item.getType())) {
                return "规则项类型仅支持 " + ITEM_TYPES;
            }
            if (!PayrollSource.isValid(item.getSource())) {
                return "规则项来源仅支持 " + ITEM_SOURCES;
            }
            // params 缺省（null）视为未配置；显式对象由算薪注册表按来源解释
            if (item.getEnabled() != null && item.getEnabled() != 0 && item.getEnabled() != 1) {
                return "规则项 enabled 仅支持 0 / 1";
            }
        }
        return null;
    }

    /** 文本长度校验：trim 后按字符数（对齐 Mock {@code textLen}） */
    private static boolean textLen(String value, int min, int max) {
        if (value == null) {
            return false;
        }
        int len = value.trim().length();
        return len >= min && len <= max;
    }
}
