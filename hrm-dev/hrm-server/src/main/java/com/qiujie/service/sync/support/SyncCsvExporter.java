package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 配置中心 CSV 行构建（纯逻辑）。
 * <p>
 * 一张表用「记录类型」列表达 ITEM / OPTION / GLOBAL / STATION 四类记录，列定义固定 16 列
 * （{@link SyncConstants#EXPORT_HEADER}）。模板与正式导出共用同一套行构建函数，模板列序不会与导入解析器脱节。
 * 与 Mock {@code syncConfigCsv.js} 的 {@code buildExport / buildTemplate / itemRow / optionRow / globalRow / stationRow}
 * 逐行对齐。
 */
public final class SyncCsvExporter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern(SyncConstants.FILENAME_DATE);

    private SyncCsvExporter() {
    }

    /**
     * 正式导出。
     *
     * @param scope ITEMS 仅定义 / ITEMS_GLOBAL 含全局默认 / ALL 含驿站覆盖
     */
    public static List<List<String>> buildExport(SyncConfigCatalog catalog, String scope) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(SyncConstants.EXPORT_HEADER));
        List<SyncConfigItem> items = catalog.items();
        items.forEach(item -> rows.add(itemRow(item)));
        for (SyncOptionSet set : catalog.optionSets()) {
            for (SyncConfigOption option : set.getOptions()) {
                rows.add(optionRow(set.getSetKey(), option));
            }
        }
        if (!"ITEMS".equals(scope)) {
            Map<String, Object> global = catalog.globalValues();
            // 全局默认值为空的项不导出：导出空值行再导回会触发必填校验，破坏「导出即导入」闭环
            for (SyncConfigItem item : items) {
                Object value = global.get(item.getItemKey());
                if (!SyncValueCodec.isBlankValue(value)) {
                    rows.add(globalRow(item, value));
                }
            }
        }
        if ("ALL".equals(scope)) {
            for (Long stationId : catalog.stationIds()) {
                Map<String, Object> overrides = catalog.overridesOf(stationId);
                if (overrides.isEmpty()) {
                    continue;
                }
                String stationName = catalog.stationName(stationId);
                for (SyncConfigItem item : items) {
                    if (overrides.containsKey(item.getItemKey())) {
                        rows.add(stationRow(item, stationName, overrides.get(item.getItemKey())));
                    }
                }
            }
        }
        return rows;
    }

    /**
     * 下载导入模板：表头 + ITEM / OPTION / GLOBAL / STATION 各一行示例（设计 B.7.3）。
     * 示例备注统一标「示例行，可删除」，避免用户把示例当真实数据导入。
     */
    public static List<List<String>> buildTemplate(SyncConfigCatalog catalog) {
        SyncConfigItem demoItem = new SyncConfigItem();
        demoItem.setItemKey("demo_sample");
        demoItem.setName("示例配置项");
        demoItem.setValueType("SINGLE_SELECT");
        demoItem.setRequired(0);
        demoItem.setDefaultValue("DEMO_OPT");
        demoItem.setUnit("");
        demoItem.setSort(9999);
        demoItem.setEnabled(1);
        demoItem.setDescription("示例行，可删除");

        SyncConfigOption demoOption = new SyncConfigOption();
        demoOption.setOptionKey("DEMO_OPT");
        demoOption.setLabel("示例选项");
        demoOption.setEnabled(1);
        demoOption.setRemark("示例行，可删除");

        List<Long> stationIds = catalog.stationIds();
        String stationName = stationIds.isEmpty() ? "城东驿站" : catalog.stationName(stationIds.get(0));

        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(SyncConstants.EXPORT_HEADER));
        rows.add(itemRow(demoItem));
        rows.add(optionRow(demoItem.getItemKey(), demoOption));
        rows.add(globalRow(demoItem, demoItem.getDefaultValue()));
        rows.add(stationRow(demoItem, stationName, demoItem.getDefaultValue()));
        return rows;
    }

    /** 导出范围合法性：未传或空串视为合法（取默认范围） */
    public static boolean exportScopeInvalid(String scope) {
        return scope != null && !scope.isEmpty() && !SyncConstants.EXPORT_SCOPES.contains(scope);
    }

    /** 导出文件名（模板固定名，便于反复下载） */
    public static String exportFilename(String scope, LocalDate today) {
        if ("TEMPLATE".equals(scope)) {
            return SyncConstants.TEMPLATE_FILENAME;
        }
        String label = "ITEMS".equals(scope) ? "配置项" : "ALL".equals(scope) ? "全部" : "含全局默认";
        return "同步配置_" + label + "_" + today.format(DAY) + ".csv";
    }

    private static List<String> itemRow(SyncConfigItem item) {
        return List.of(
                "ITEM",
                nvl(item.getItemKey()),
                nvl(item.getName()),
                nvl(item.getValueType()),
                Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getRequired())) ? "是" : "否",
                valueText(item, defaultTyped(item)),
                nvl(item.getUnit()),
                constraintsText(item),
                item.getSort() == null ? "" : String.valueOf(item.getSort()),
                Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getEnabled())) ? "是" : "否",
                "", "", "", "", "",
                nvl(item.getDescription()));
    }

    private static List<String> optionRow(String setKey, SyncConfigOption option) {
        return List.of(
                "OPTION",
                nvl(setKey),
                "", "", "", "", "", "", "",
                Boolean.TRUE.equals(SyncConfigValidator.toBool(option.getEnabled())) ? "是" : "否",
                nvl(option.getOptionKey()),
                nvl(option.getLabel()),
                extraAttrsText(option.getExtraAttrs()),
                "", "",
                nvl(option.getRemark()));
    }

    private static List<String> globalRow(SyncConfigItem item, Object value) {
        return List.of("GLOBAL", nvl(item.getItemKey()), "", "", "", "", "", "", "", "", "", "", "", "",
                valueText(item, value), "");
    }

    private static List<String> stationRow(SyncConfigItem item, String stationName, Object value) {
        return List.of("STATION", nvl(item.getItemKey()), "", "", "", "", "", "", "", "", "", "", "", 
                nvl(stationName), valueText(item, value), "");
    }

    /** 配置项默认值的类型化（落库为字符串，导出按 valueType 还原） */
    private static Object defaultTyped(SyncConfigItem item) {
        return SyncValueCodec.toTyped(item.getValueType(), item.getDefaultValue());
    }

    private static String valueText(SyncConfigItem item, Object value) {
        if (SyncValueCodec.isBlankValue(value)) {
            return "";
        }
        return String.valueOf(value);
    }

    private static String constraintsText(SyncConfigItem item) {
        Map<String, Object> c = item.getConstraints();
        if (c == null) {
            return "";
        }
        if ("NUMBER".equals(item.getValueType())) {
            String range = display(c.get("min")) + "-" + display(c.get("max"));
            return Boolean.TRUE.equals(SyncConfigValidator.toBool(c.get("integerOnly"))) ? range + " 的整数" : range;
        }
        if ("TEXT".equals(item.getValueType())) {
            Object minLen = c.get("minLen");
            Object maxLen = c.get("maxLen");
            return maxLen == null || (minLen != null && minLen.equals(maxLen))
                    ? display(minLen) + " 字符"
                    : display(minLen) + "-" + display(maxLen) + " 字符";
        }
        return "";
    }

    private static String extraAttrsText(Map<String, Object> extraAttrs) {
        if (extraAttrs == null || extraAttrs.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : extraAttrs.entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String display(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
