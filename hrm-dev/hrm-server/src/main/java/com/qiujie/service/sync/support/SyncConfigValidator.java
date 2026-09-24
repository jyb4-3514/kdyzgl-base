package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 配置项 / 选项值校验器（纯逻辑，无 DB 依赖）。
 * <p>
 * 值校验矩阵逐类型对齐 Mock {@code syncConfigStore.js#validateValue}（设计 C.1/C.3），文案也逐字对齐，
 * 便于前端按 9506 / 9507 分流展示「填错」与「没填」。
 * <p>
 * 为什么拆纯类：约束校验是「一份实现、三处调用」（保存全局默认 / 保存驿站覆盖 / CSV 导入行校验），
 * 散在 Service 里必然出现两份实现（Mock 注释已点明该风险）。
 */
public final class SyncConfigValidator {

    private static final Pattern ITEM_KEY = Pattern.compile(SyncConstants.ITEM_KEY_PATTERN);
    private static final Pattern OPTION_KEY = Pattern.compile(SyncConstants.OPTION_KEY_PATTERN);

    private SyncConfigValidator() {
    }

    /** 校验结果（带 95xx / 400 业务码，供各写入口直接映射响应） */
    public record Error(int code, String message) {
    }

    /** 泛型结果：值 + 错误文案（error 非空即失败） */
    public record Outcome<T>(T value, String error) {
        public static <T> Outcome<T> ok(T value) {
            return new Outcome<>(value, null);
        }

        public static <T> Outcome<T> err(String error) {
            return new Outcome<>(null, error);
        }

        public boolean isError() {
            return error != null;
        }
    }

    // ==================== Key 校验 ====================

    /** 配置项 Key 校验：合法返回 null，否则返回人话文案 */
    public static String validateItemKey(String itemKey) {
        return ITEM_KEY.matcher(itemKey == null ? "" : itemKey).matches()
                ? null
                : "配置项 Key 须以小写字母开头，仅含小写字母、数字与下划线，长度 2-40，当前为「" + itemKey + "」";
    }

    /** 选项 Key 校验 */
    public static String validateOptionKey(String optionKey) {
        return OPTION_KEY.matcher(optionKey == null ? "" : optionKey).matches()
                ? null
                : "选项 Key 须以字母开头，仅含字母、数字、下划线或短横线，长度 1-40，当前为「" + optionKey + "」";
    }

    // ==================== 值校验 ====================

    /**
     * 校验某配置项的取值，返回人话错误文案（null = 通过）。
     *
     * @param item    配置项定义
     * @param options 该配置项关联选项集的<b>可选项</b>（集合停用时传空列表）
     * @param value   待校验的类型化值
     * @return 错误文案或 null
     */
    public static String validateValue(SyncConfigItem item, List<SyncConfigOption> options, Object value) {
        if (SyncValueCodec.isBlankValue(value)) {
            return Boolean.TRUE.equals(toBool(item.getRequired())) ? "请填写「" + item.getName() + "」" : null;
        }
        String valueType = item.getValueType();
        if (valueType == null) {
            return null;
        }
        return switch (valueType) {
            case "SINGLE_SELECT" -> validateSingleSelect(item, options, value);
            case "NUMBER" -> validateNumber(item, value);
            case "TEXT" -> validateText(item, value);
            case "TIME" -> validateTime(item, value);
            case "TIME_RANGE" -> validateTimeRange(item, value);
            default -> null;
        };
    }

    /**
     * 首个错误：必填置空单独给 9507，其余约束不满足给 9506（前端据此区分「没填」与「填错」）。
     */
    public static Error firstError(SyncConfigItem item, List<SyncConfigOption> options, Object value) {
        if (SyncValueCodec.isBlankValue(value) && Boolean.TRUE.equals(toBool(item.getRequired()))) {
            return new Error(9507, "请填写「" + item.getName() + "」");
        }
        String message = validateValue(item, options, value);
        return message == null ? null : new Error(9506, message);
    }

    private static String validateSingleSelect(SyncConfigItem item, List<SyncConfigOption> options, Object value) {
        List<SyncConfigOption> selectable = new ArrayList<>();
        if (options != null) {
            for (SyncConfigOption option : options) {
                if (Boolean.TRUE.equals(toBool(option.getEnabled()))) {
                    selectable.add(option);
                }
            }
        }
        if (selectable.isEmpty()) {
            return "「" + item.getName() + "」暂无可选项，请先在「配置项与选项集」中添加候选项";
        }
        String text = String.valueOf(value);
        SyncConfigOption hit = null;
        for (SyncConfigOption option : options) {
            if (text.equals(option.getOptionKey())) {
                hit = option;
                break;
            }
        }
        if (hit == null) {
            return "「" + item.getName() + "」选择的选项不存在，请重新选择";
        }
        if (!Boolean.TRUE.equals(toBool(hit.getEnabled()))) {
            return "「" + item.getName() + "」选择的「" + hit.getLabel() + "」已停用，请重新选择";
        }
        return null;
    }

    private static String validateNumber(SyncConfigItem item, Object value) {
        Map<String, Object> c = item.getConstraints() == null ? Map.of() : item.getConstraints();
        Double num = toDouble(value);
        if (num == null) {
            return "「" + item.getName() + "」须为数字，当前为 " + value;
        }
        Object minRaw = c.get("min");
        Object maxRaw = c.get("max");
        if (minRaw != null || maxRaw != null) {
            double min = minRaw == null ? Double.NEGATIVE_INFINITY : ((Number) minRaw).doubleValue();
            double max = maxRaw == null ? Double.POSITIVE_INFINITY : ((Number) maxRaw).doubleValue();
            if (num < min || num > max) {
                String unit = item.getUnit() == null || item.getUnit().isEmpty() ? "" : " " + item.getUnit();
                return "「" + item.getName() + "」须在 " + display(minRaw) + "-" + display(maxRaw) + unit
                        + " 之间，当前为 " + value;
            }
        }
        if (Boolean.TRUE.equals(toBool(c.get("integerOnly"))) && !isInteger(num)) {
            return "「" + item.getName() + "」须为整数，当前为 " + value;
        }
        Object stepRaw = c.get("step");
        if (stepRaw != null) {
            double step = ((Number) stepRaw).doubleValue();
            if (step > 1 && isInteger(num) && num % step != 0) {
                return "「" + item.getName() + "」须为 " + display(stepRaw) + " 的整数倍，当前为 " + value;
            }
        }
        return null;
    }

    private static String validateText(SyncConfigItem item, Object value) {
        Map<String, Object> c = item.getConstraints() == null ? Map.of() : item.getConstraints();
        String text = String.valueOf(value).trim();
        int min = c.get("minLen") == null ? 0 : ((Number) c.get("minLen")).intValue();
        int max = c.get("maxLen") == null ? Integer.MAX_VALUE : ((Number) c.get("maxLen")).intValue();
        if (text.length() < min || text.length() > max) {
            return "「" + item.getName() + "」长度须为 " + display(c.get("minLen")) + "-" + display(c.get("maxLen"))
                    + " 字符，当前 " + text.length() + " 字符";
        }
        Object pattern = c.get("pattern");
        if (pattern != null) {
            try {
                if (!Pattern.compile(String.valueOf(pattern)).matcher(text).matches()) {
                    Object hint = c.get("patternHint");
                    return "「" + item.getName() + "」格式须为 " + (hint == null ? pattern : hint)
                            + "，当前为「" + value + "」";
                }
            } catch (RuntimeException e) {
                // 非法正则按配置错误跳过：不能因为一条脏约束把整个保存流程挡死
                return null;
            }
        }
        return null;
    }

    private static String validateTime(SyncConfigItem item, Object value) {
        String text = String.valueOf(value);
        if (!SyncClock.isClock(text)) {
            return "「" + item.getName() + "」时间格式须为 HH:mm（如 08:00），当前为「" + value + "」";
        }
        Map<String, Object> c = item.getConstraints() == null ? Map.of() : item.getConstraints();
        int minute = SyncClock.minutesOfClock(text);
        Object min = c.get("min");
        if (min != null && minute < SyncClock.minutesOfClock(String.valueOf(min))) {
            return "「" + item.getName() + "」不得早于 " + min + "，当前为 " + value;
        }
        Object max = c.get("max");
        if (max != null && minute > SyncClock.minutesOfClock(String.valueOf(max))) {
            return "「" + item.getName() + "」不得晚于 " + max + "，当前为 " + value;
        }
        return null;
    }

    private static String validateTimeRange(SyncConfigItem item, Object value) {
        Map<String, Object> c = item.getConstraints();
        boolean allowEnd2400 = c == null || !Boolean.FALSE.equals(c.get("allowEnd2400"));
        List<String> parts = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object part : list) {
                parts.add(part == null ? "" : String.valueOf(part).trim());
            }
        } else {
            for (String part : String.valueOf(value).split("-")) {
                parts.add(part.trim());
            }
        }
        String start = parts.isEmpty() ? "" : parts.get(0);
        String end = parts.size() < 2 ? "" : parts.get(1);
        // 结束位允许 24:00（仅有结束位是「跨零点收班」的合法端点）；是否放行由 allowEnd2400 决定，
        // 与 SyncClock.isEndClock 及时段模板口径一致，故格式判定用 isEndClock 而非 isClock
        if (!SyncClock.isClock(start) || !SyncClock.isEndClock(end)) {
            return "「" + item.getName() + "」时间格式须为 HH:mm（如 08:00），当前为「" + start + " - " + end + "」";
        }
        if (start.compareTo(end) >= 0 && !"24:00".equals(end)) {
            return "「" + item.getName() + "」的结束时间须晚于开始时间，当前为 " + start + " - " + end;
        }
        if ("24:00".equals(end) && !allowEnd2400) {
            return "「" + item.getName() + "」的结束时间不允许为 24:00";
        }
        return null;
    }

    // ==================== 约束 / 附加属性规范化 ====================

    /** 取值约束只保留本值类型认识的键，未知键忽略（向前兼容前端多传字段） */
    public static Outcome<Map<String, Object>> normalizeConstraints(String valueType, Map<String, Object> raw) {
        if (raw == null) {
            return Outcome.ok(null);
        }
        if ("NUMBER".equals(valueType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("min", num(raw.get("min")));
            result.put("max", num(raw.get("max")));
            Object step = num(raw.get("step"));
            result.put("step", step == null ? 1 : step);
            result.put("integerOnly", Boolean.TRUE.equals(toBool(raw.get("integerOnly"))));
            Object precision = num(raw.get("precision"));
            result.put("precision", precision == null ? 0 : precision);
            if (result.get("min") != null && result.get("max") != null
                    && ((Number) result.get("min")).doubleValue() > ((Number) result.get("max")).doubleValue()) {
                return Outcome.err("数值约束的最小值不得大于最大值");
            }
            return Outcome.ok(result);
        }
        if ("TEXT".equals(valueType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            Object minLen = num(raw.get("minLen"));
            result.put("minLen", minLen == null ? 0 : minLen);
            result.put("maxLen", num(raw.get("maxLen")));
            result.put("pattern", raw.get("pattern"));
            result.put("patternHint", raw.get("patternHint"));
            if (result.get("maxLen") != null
                    && ((Number) result.get("minLen")).doubleValue() > ((Number) result.get("maxLen")).doubleValue()) {
                return Outcome.err("文本长度约束的最小值不得大于最大值");
            }
            return Outcome.ok(result);
        }
        if ("TIME".equals(valueType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("min", raw.get("min"));
            result.put("max", raw.get("max"));
            return Outcome.ok(result);
        }
        if ("TIME_RANGE".equals(valueType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("allowEnd2400", !Boolean.FALSE.equals(raw.get("allowEnd2400")));
            return Outcome.ok(result);
        }
        return Outcome.ok(null);
    }

    /** 附加属性结构由所属选项集约定，不认识的键忽略 */
    public static Outcome<Map<String, Object>> normalizeExtraAttrs(String setKey, Map<String, Object> raw) {
        if (raw == null) {
            return Outcome.ok(null);
        }
        if (SyncConstants.SET_COLLECT_FREQUENCY.equals(setKey)) {
            Double minutes = toDouble(raw.get("intervalMinutes"));
            if (minutes == null || minutes <= 0) {
                return Outcome.err("采集间隔须为大于 0 的分钟数");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("intervalMinutes", minutes);
            return Outcome.ok(value);
        }
        if (SyncConstants.SET_TIME_TEMPLATE.equals(setKey)) {
            String start = raw.get("startTime") == null ? "" : String.valueOf(raw.get("startTime"));
            String end = raw.get("endTime") == null ? "" : String.valueOf(raw.get("endTime"));
            if (!SyncClock.isClock(start)) {
                return Outcome.err("时段模板的开始时间格式须为 HH:mm");
            }
            if (!SyncClock.isEndClock(end)) {
                return Outcome.err("时段模板的结束时间格式须为 HH:mm");
            }
            if (SyncClock.minutesOfClock(start) >= SyncClock.minutesOfClock(end)) {
                return Outcome.err("时段模板的结束时间须晚于开始时间");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("startTime", start);
            value.put("endTime", end);
            return Outcome.ok(value);
        }
        return Outcome.ok(null);
    }

    // ==================== 小工具 ====================

    /** 0/1 或布尔 → Boolean（null 视为 false） */
    public static Boolean toBool(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value).trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object num(Object value) {
        if (value == null || (value instanceof String text && text.isEmpty())) {
            return null;
        }
        Double parsed = toDouble(value);
        return parsed == null ? null : parsed;
    }

    private static boolean isInteger(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value);
    }

    private static String display(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
