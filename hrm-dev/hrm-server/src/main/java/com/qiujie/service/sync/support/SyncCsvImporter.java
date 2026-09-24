package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置中心 CSV 导入解析与逐行校验（纯逻辑）。
 * <p>
 * 解析 + 校验产出一份「预览 + 待执行动作」，dryRun 与正式导入<b>共用本函数</b>，保证「预览看到的行级结论」
 * 与「真正导入的结果」不会因为两套解析逻辑而打架（Mock 注释已点明该约束）。
 * 行级失败只标记该行，不让整体崩（与 employee 导入的 5003 模式同源）。
 * 与 Mock {@code syncConfigCsv.js} 的 {@code buildImportPlan / validateRow} 逐分支对齐。
 */
public final class SyncCsvImporter {

    private static final Pattern OPTION_KEY = Pattern.compile(SyncConstants.OPTION_KEY_PATTERN);

    private static final String LEVEL_OK = "OK";
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_FAILED = "FAILED";
    private static final String KIND_CREATE = "CREATE";
    private static final String KIND_UPDATE = "UPDATE";
    private static final String KIND_SKIP = "SKIP";

    private SyncCsvImporter() {
    }

    /** 导入行预览 */
    public record ImportRow(int rowNo, String type, String itemKey, String optionKey, String stationName,
                            String level, String message) {
    }

    /** 汇总计数 */
    public record Summary(int total, int ok, int failed, int warning) {
    }

    /** 计划计数 */
    public record PlanCount(int create, int update, int skip, int conflict) {
    }

    /** 解析结果（预览 + 动作） */
    public record ImportPlanResult(List<ImportRow> rows, Summary summary, PlanCount plan, List<Action> actions) {
    }

    /** 解析结论（对齐 Mock 的 {code,message,data}） */
    public record PlanOutcome(int code, String message, ImportPlanResult data) {
        public static PlanOutcome ok(ImportPlanResult data) {
            return new PlanOutcome(200, null, data);
        }

        public static PlanOutcome err(int code, String message) {
            return new PlanOutcome(code, message, null);
        }

        public boolean isOk() {
            return code == 200;
        }
    }

    /** 待执行动作（type=ITEM/OPTION/GLOBAL/STATION；kind=CREATE/UPDATE/SKIP） */
    @Data
    @NoArgsConstructor
    public static class Action {
        private String type;
        private String kind;
        private String itemKey;
        private String optionKey;
        private String setKey;
        private Long stationId;
        private Object value;
        private Map<String, Object> payload;
    }

    /**
     * 解析 + 逐行校验。
     *
     * @param content     CSV 文本（外部输入）
     * @param onConflict  冲突策略（已大写）
     * @param catalog     配置中心只读快照
     * @param rowLimit    单次导入行上限（外置 {@code hrm.algo.sync.importRowLimit}）
     */
    public static PlanOutcome buildImportPlan(String content, String onConflict, SyncConfigCatalog catalog,
                                              int rowLimit) {
        if (!SyncConstants.CONFLICT_STRATEGIES.contains(onConflict)) {
            return PlanOutcome.err(9509, "冲突策略仅支持 OVERWRITE / SKIP / APPEND");
        }
        List<List<String>> rows = SyncCsvCodec.parse(content);
        if (rows.isEmpty() || rows.stream().allMatch(row -> row.stream().allMatch(cell -> cell.trim().isEmpty()))) {
            return PlanOutcome.err(9508, "导入内容为空，请检查文件");
        }
        List<String> header = new ArrayList<>();
        for (String cell : rows.get(0)) {
            header.add(cell.trim());
        }
        if (header.size() != SyncConstants.EXPORT_HEADER.size()
                || !header.equals(SyncConstants.EXPORT_HEADER)) {
            return PlanOutcome.err(9508, "表头与模板不一致，第 1 行应为 " + SyncConstants.EXPORT_HEADER.size()
                    + " 列：" + String.join(",", SyncConstants.EXPORT_HEADER));
        }
        List<List<String>> dataRows = rows.subList(1, rows.size());
        if (dataRows.size() > rowLimit) {
            // 与 Mock IMPORT_CODE.ROW_LIMIT 同码（5002），文案由 GlobalExceptionHandler 兜底
            return PlanOutcome.err(5002, null);
        }

        Map<String, Integer> seen = new LinkedHashMap<>();
        // 同一文件内先声明的 ITEM 行可能被后面的 GLOBAL/STATION 行引用，先按行预生成草稿配置项，
        // 否则「一个文件即一份完整配置快照」在新增配置项时会因为「解析时配置项还不存在」而整批失败
        Map<String, List<String>> pendingItems = new LinkedHashMap<>();
        for (List<String> fields : dataRows) {
            if (!"ITEM".equals(field(fields, 0))) {
                continue;
            }
            String itemKey = field(fields, 1);
            if (!itemKey.isEmpty()) {
                pendingItems.put(itemKey, fields);
            }
        }

        List<ImportRow> preview = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            RowResult result = validateRow(dataRows.get(i), i + 2, seen, onConflict, pendingItems, catalog);
            preview.add(result.row());
            if (result.action() != null) {
                actions.add(result.action());
            }
        }
        int failed = (int) preview.stream().filter(row -> LEVEL_FAILED.equals(row.level())).count();
        int warning = (int) preview.stream().filter(row -> LEVEL_WARNING.equals(row.level())).count();
        int total = dataRows.size();
        Summary summary = new Summary(total, total - failed, failed, warning);
        PlanCount plan = new PlanCount(
                (int) actions.stream().filter(a -> KIND_CREATE.equals(a.getKind())).count(),
                (int) actions.stream().filter(a -> KIND_UPDATE.equals(a.getKind())).count(),
                (int) actions.stream().filter(a -> KIND_SKIP.equals(a.getKind())).count(),
                (int) preview.stream().filter(row -> LEVEL_FAILED.equals(row.level())
                        && row.message().contains("不允许覆盖")).count());
        return PlanOutcome.ok(new ImportPlanResult(preview, summary, plan, actions));
    }

    private record RowResult(ImportRow row, Action action) {
    }

    private record ExtraParse(Map<String, Object> value, List<String> warnings, String error) {
    }

    // ==================== 逐行校验 ====================

    private static RowResult validateRow(List<String> fields, int rowNo, Map<String, Integer> seen,
                                         String onConflict, Map<String, List<String>> pendingItems,
                                         SyncConfigCatalog catalog) {
        if (fields.size() != SyncConstants.EXPORT_HEADER.size()) {
            return failed(rowNo, "", "第 " + rowNo + " 行：列数不正确，应为 " + SyncConstants.EXPORT_HEADER.size()
                    + " 列，当前 " + fields.size() + " 列", fields);
        }
        String type = field(fields, 0);
        // 大小写敏感（设计 D.5 第 5 点）：小写不归一化，直接行级失败并指出正确写法
        if (!SyncConstants.RECORD_TYPES.contains(type)) {
            return failed(rowNo, "", "第 " + rowNo + " 行：记录类型须为大写枚举 "
                    + String.join(" / ", SyncConstants.RECORD_TYPES) + " 之一，当前为「" + type + "」"
                    + upperHint(SyncConstants.RECORD_TYPES, type), fields);
        }
        String uniqueKey = judgmentKey(type, fields);
        if (uniqueKey != null && seen.containsKey(uniqueKey)) {
            return failed(rowNo, type, "第 " + rowNo + " 行：" + uniqueKey + " 在本文件中重复出现（第 "
                    + seen.get(uniqueKey) + " 行已定义）", fields);
        }
        if (uniqueKey != null) {
            seen.put(uniqueKey, rowNo);
        }
        return switch (type) {
            case "ITEM" -> validateItemRow(fields, rowNo, onConflict, catalog);
            case "OPTION" -> validateOptionRow(fields, rowNo, onConflict, catalog);
            case "GLOBAL" -> validateGlobalRow(fields, rowNo, onConflict, pendingItems, catalog);
            default -> validateStationRow(fields, rowNo, onConflict, pendingItems, catalog);
        };
    }

    private static RowResult validateItemRow(List<String> fields, int rowNo, String onConflict,
                                             SyncConfigCatalog catalog) {
        String itemKey = field(fields, 1);
        String name = field(fields, 2);
        String valueType = field(fields, 3);
        String requiredText = field(fields, 4);
        String defaultValue = field(fields, 5);
        String unit = field(fields, 6);
        String constraintText = field(fields, 7);
        String sortText = field(fields, 8);
        String enabledText = field(fields, 9);
        String remark = field(fields, 15);

        String keyError = SyncConfigValidator.validateItemKey(itemKey);
        if (keyError != null) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：" + keyError, fields);
        }
        if (!SyncConstants.VALUE_TYPES.contains(valueType)) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：值类型「" + valueType + "」不合法，仅支持 "
                    + String.join(" / ", SyncConstants.VALUE_TYPES)
                    + upperHint(SyncConstants.VALUE_TYPES, valueType), fields);
        }
        if (name.isEmpty() || name.length() > 20) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：配置项名称须为 1-20 字符", fields);
        }
        if (invalidCnBool(requiredText)) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：是否必填仅支持「是」「否」", fields);
        }
        if (invalidCnBool(enabledText)) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：是否启用仅支持「是」「否」", fields);
        }
        Boolean required = cnBool(requiredText);
        boolean enabled = !Boolean.FALSE.equals(cnBool(enabledText));

        SyncConfigItem existing = catalog.findItem(itemKey);
        if (existing != null && !existing.getValueType().equals(valueType)) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：配置项「" + itemKey + "」值类型为 "
                    + existing.getValueType() + "，不允许改为 " + valueType, fields);
        }
        Integer sort = null;
        if (sortText.isEmpty()) {
            sort = existing == null ? null : existing.getSort();
        } else {
            try {
                sort = Integer.valueOf(sortText);
            } catch (NumberFormatException e) {
                return failed(rowNo, "ITEM", "第 " + rowNo + " 行：排序须为 0-9999 的整数", fields);
            }
            if (sort < 0 || sort > 9999) {
                return failed(rowNo, "ITEM", "第 " + rowNo + " 行：排序须为 0-9999 的整数", fields);
            }
        }

        SyncConfigValidator.Outcome<Map<String, Object>> constraints =
                parseConstraintsText(valueType, constraintText);
        if (constraints.isError()) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：" + constraints.error(), fields);
        }
        String optionSetKey = "SINGLE_SELECT".equals(valueType)
                ? (existing != null ? existing.getOptionSetKey() : itemKey)
                : null;
        if ("SINGLE_SELECT".equals(valueType) && catalog.findOptionSet(optionSetKey) == null) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：关联的选项集「" + optionSetKey
                    + "」不存在，单选项须复用已有选项集", fields);
        }

        Object value = defaultValue.isEmpty()
                ? (existing == null ? null : SyncValueCodec.toTyped(existing.getValueType(), existing.getDefaultValue()))
                : SyncValueCodec.coerceFromText(valueType, defaultValue);
        SyncConfigItem draft = new SyncConfigItem();
        draft.setItemKey(itemKey);
        draft.setName(name);
        draft.setValueType(valueType);
        draft.setRequired(Boolean.TRUE.equals(required) ? 1 : 0);
        draft.setUnit(unit);
        draft.setConstraints(constraints.value());
        draft.setOptionSetKey(optionSetKey);
        draft.setScope(existing != null ? existing.getScope() : "STATION");
        if (!SyncValueCodec.isBlankValue(value)) {
            String message = SyncConfigValidator.validateValue(draft, catalog.optionsOfSet(optionSetKey), value);
            if (message != null) {
                return failed(rowNo, "ITEM", "第 " + rowNo + " 行：" + message, fields);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("required", Boolean.TRUE.equals(required));
        payload.put("defaultValue", value);
        payload.put("unit", unit);
        payload.put("constraints", constraints.value());
        payload.put("enabled", enabled);
        payload.put("description", remark);
        if (sort != null) {
            payload.put("sort", sort);
        }
        if (existing == null) {
            payload.put("itemKey", itemKey);
            payload.put("valueType", valueType);
            payload.put("optionSetKey", optionSetKey);
            payload.put("scope", "STATION");
        }

        String conflictError = resolveConflictError(existing != null, onConflict);
        if (conflictError != null) {
            return failed(rowNo, "ITEM", "第 " + rowNo + " 行：配置项 Key「" + itemKey + "」已存在，追加策略不允许覆盖",
                    fields);
        }
        boolean skip = isSkip(existing != null, onConflict);
        Action action = skip ? action("ITEM", KIND_SKIP, itemKey, null, null, null, null, null)
                : action("ITEM", kindOf(existing != null, onConflict), itemKey, null, null, null, null, payload);
        ImportRow row = new ImportRow(rowNo, "ITEM", itemKey, "", "", LEVEL_OK,
                skip ? "已跳过（冲突策略：跳过）" : "通过");
        return new RowResult(row, action);
    }

    private static RowResult validateOptionRow(List<String> fields, int rowNo, String onConflict,
                                               SyncConfigCatalog catalog) {
        String setKey = field(fields, 1);
        String enabledText = field(fields, 9);
        String optionKey = field(fields, 10);
        String label = field(fields, 11);
        String extraText = field(fields, 12);
        String remark = field(fields, 15);

        SyncOptionSet set = catalog.findOptionSet(setKey);
        if (set == null) {
            return failed(rowNo, "OPTION", "第 " + rowNo + " 行：关联的选项集「" + setKey + "」不存在", fields);
        }
        if (!OPTION_KEY.matcher(optionKey).matches()) {
            return failed(rowNo, "OPTION", "第 " + rowNo
                    + " 行：选项 Key 须以字母开头，仅含字母、数字、下划线或短横线，长度 1-40", fields);
        }
        if (label.isEmpty() || label.length() > 20) {
            return failed(rowNo, "OPTION", "第 " + rowNo + " 行：选项显示名须为 1-20 字符", fields);
        }
        if (invalidCnBool(enabledText)) {
            return failed(rowNo, "OPTION", "第 " + rowNo + " 行：是否启用仅支持「是」「否」", fields);
        }
        boolean enabled = !Boolean.FALSE.equals(cnBool(enabledText));
        ExtraParse extra = parseExtraAttrs(setKey, extraText);
        if (extra.error() != null) {
            return failed(rowNo, "OPTION", "第 " + rowNo + " 行：" + extra.error(), fields);
        }
        SyncConfigOption existing = null;
        for (SyncConfigOption option : set.getOptions()) {
            if (option.getOptionKey().equals(optionKey)) {
                existing = option;
                break;
            }
        }
        String conflictError = resolveConflictError(existing != null, onConflict);
        if (conflictError != null) {
            return failed(rowNo, "OPTION", "第 " + rowNo + " 行：选项 Key「" + optionKey + "」已存在，追加策略不允许覆盖",
                    fields);
        }
        boolean skip = isSkip(existing != null, onConflict);
        String message = skip ? "已跳过（冲突策略：跳过）"
                : (!extra.warnings().isEmpty() ? String.join("；", extra.warnings()) : "通过");
        String level = !extra.warnings().isEmpty() && !skip ? LEVEL_WARNING : LEVEL_OK;
        Action action;
        if (skip) {
            action = action("OPTION", KIND_SKIP, null, optionKey, setKey, null, null, null);
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("label", label);
            payload.put("extraAttrs", extra.value());
            payload.put("enabled", enabled);
            payload.put("remark", remark.isEmpty() ? null : remark);
            action = action("OPTION", kindOf(existing != null, onConflict), null, optionKey, setKey, null, null, payload);
        }
        return new RowResult(new ImportRow(rowNo, "OPTION", setKey, optionKey, "", level, message), action);
    }

    private static RowResult validateGlobalRow(List<String> fields, int rowNo, String onConflict,
                                               Map<String, List<String>> pendingItems, SyncConfigCatalog catalog) {
        String itemKey = field(fields, 1);
        SyncConfigItem item = lookupItem(itemKey, pendingItems, catalog);
        if (item == null) {
            return failed(rowNo, "GLOBAL", "第 " + rowNo + " 行：配置项「" + itemKey + "」不存在", fields);
        }
        Object value = SyncValueCodec.coerceFromText(item.getValueType(), field(fields, 14));
        SyncConfigValidator.Error error = SyncConfigValidator.firstError(item, catalog.optionsOfSet(item.getOptionSetKey()), value);
        if (error != null) {
            return failed(rowNo, "GLOBAL", "第 " + rowNo + " 行：" + error.message(), fields);
        }
        boolean exists = !SyncValueCodec.isBlankValue(catalog.globalValues().get(itemKey));
        if (resolveConflictError(exists, onConflict) != null) {
            return failed(rowNo, "GLOBAL", "第 " + rowNo + " 行：配置项「" + itemKey
                    + "」的全局默认已存在，追加策略不允许覆盖", fields);
        }
        boolean skip = isSkip(exists, onConflict);
        String message = skip ? "已跳过（冲突策略：跳过）" : "通过";
        Action action = skip ? action("GLOBAL", KIND_SKIP, itemKey, null, null, null, null, null)
                : action("GLOBAL", kindOf(exists, onConflict), itemKey, null, null, null, value, null);
        return new RowResult(new ImportRow(rowNo, "GLOBAL", itemKey, "", "", LEVEL_OK, message), action);
    }

    private static RowResult validateStationRow(List<String> fields, int rowNo, String onConflict,
                                                Map<String, List<String>> pendingItems, SyncConfigCatalog catalog) {
        String itemKey = field(fields, 1);
        String stationText = field(fields, 13);
        SyncConfigItem item = lookupItem(itemKey, pendingItems, catalog);
        if (item == null) {
            return failed(rowNo, "STATION", "第 " + rowNo + " 行：配置项「" + itemKey + "」不存在", fields);
        }
        if (!"STATION".equals(item.getScope())) {
            return failed(rowNo, "STATION", "第 " + rowNo + " 行：「" + item.getName() + "」为全局配置项，不支持驿站覆盖",
                    fields);
        }
        Long stationId = catalog.stationIdByName(stationText);
        if (stationId == null) {
            return failed(rowNo, "STATION", "第 " + rowNo + " 行：驿站「" + stationText + "」不存在，请核对名称", fields);
        }
        Object value = SyncValueCodec.coerceFromText(item.getValueType(), field(fields, 14));
        SyncConfigValidator.Error error = SyncConfigValidator.firstError(item, catalog.optionsOfSet(item.getOptionSetKey()), value);
        if (error != null) {
            return failed(rowNo, "STATION", "第 " + rowNo + " 行：" + error.message(), fields);
        }
        boolean exists = catalog.overrideKeys(stationId).contains(itemKey);
        if (resolveConflictError(exists, onConflict) != null) {
            return failed(rowNo, "STATION", "第 " + rowNo + " 行：驿站「" + stationText + "」的「" + item.getName()
                    + "」已覆盖，追加策略不允许覆盖", fields);
        }
        boolean skip = isSkip(exists, onConflict);
        String message = skip ? "已跳过（冲突策略：跳过）" : "通过";
        Action action = skip
                ? action("STATION", KIND_SKIP, itemKey, null, null, stationId, null, null)
                : action("STATION", kindOf(exists, onConflict), itemKey, null, null, stationId, value, null);
        return new RowResult(new ImportRow(rowNo, "STATION", itemKey, "", stationText, LEVEL_OK, message), action);
    }

    // ==================== 工具 ====================

    /** GLOBAL/STATION 行的配置项解析：优先取已存在的，其次取本文件 ITEM 行预生成的草稿 */
    private static SyncConfigItem lookupItem(String itemKey, Map<String, List<String>> pendingItems,
                                             SyncConfigCatalog catalog) {
        SyncConfigItem existed = catalog.findItem(itemKey);
        if (existed != null) {
            return existed;
        }
        List<String> fields = pendingItems.get(itemKey);
        return fields == null ? null : draftItemOf(fields);
    }

    private static SyncConfigItem draftItemOf(List<String> fields) {
        String valueType = field(fields, 3);
        if (!SyncConstants.VALUE_TYPES.contains(valueType)) {
            return null;
        }
        SyncConfigValidator.Outcome<Map<String, Object>> constraints =
                parseConstraintsText(valueType, field(fields, 7));
        if (constraints.isError()) {
            return null;
        }
        String itemKey = field(fields, 1);
        SyncConfigItem item = new SyncConfigItem();
        item.setItemKey(itemKey);
        item.setName(field(fields, 2));
        item.setValueType(valueType);
        item.setRequired(Boolean.TRUE.equals(cnBool(field(fields, 4))) ? 1 : 0);
        item.setUnit(field(fields, 6));
        item.setConstraints(constraints.value());
        item.setOptionSetKey("SINGLE_SELECT".equals(valueType) ? itemKey : null);
        item.setScope("STATION");
        return item;
    }

    private static String judgmentKey(String type, List<String> fields) {
        return switch (type) {
            case "ITEM" -> "配置项 Key「" + field(fields, 1) + "」";
            case "OPTION" -> "选项 Key「" + field(fields, 10) + "」";
            case "GLOBAL" -> "配置项「" + field(fields, 1) + "」的全局默认";
            default -> "驿站「" + field(fields, 13) + "」的配置项「" + field(fields, 1) + "」";
        };
    }

    /** 冲突策略 → 动作类型；返回非 null 表示「追加策略不允许覆盖」 */
    private static String resolveConflictError(boolean exists, String onConflict) {
        if (!exists) {
            return null;
        }
        return "APPEND".equals(onConflict) ? "conflict" : null;
    }

    private static boolean isSkip(boolean exists, String onConflict) {
        return exists && !"OVERWRITE".equals(onConflict) && !"APPEND".equals(onConflict);
    }

    private static String kindOf(boolean exists, String onConflict) {
        return exists ? KIND_UPDATE : KIND_CREATE;
    }

    private static Action action(String type, String kind, String itemKey, String optionKey, String setKey,
                                 Long stationId, Object value, Map<String, Object> payload) {
        Action action = new Action();
        action.setType(type);
        action.setKind(kind);
        action.setItemKey(itemKey);
        action.setOptionKey(optionKey);
        action.setSetKey(setKey);
        action.setStationId(stationId);
        action.setValue(value);
        action.setPayload(payload);
        return action;
    }

    private static RowResult failed(int rowNo, String type, String message, List<String> fields) {
        ImportRow row = new ImportRow(rowNo, type, field(fields, 1), field(fields, 10), field(fields, 13),
                LEVEL_FAILED, message);
        return new RowResult(row, null);
    }

    private static String field(List<String> fields, int index) {
        if (fields == null || index >= fields.size()) {
            return "";
        }
        String value = fields.get(index);
        return value == null ? "" : value.trim();
    }

    /** 大小写不匹配的纠正提示：仅当大写形式合法时才给建议 */
    private static String upperHint(List<String> list, String value) {
        String upper = value == null ? "" : value.toUpperCase();
        return list.contains(upper) && !upper.equals(value) ? "，请改为「" + upper + "」" : "";
    }

    /** 「是」→true，「否」→false，空→undefined(null)，其余→null（由 {@link #invalidCnBool} 判定非法） */
    private static Boolean cnBool(String text) {
        String value = text == null ? "" : text.trim();
        if ("是".equals(value)) {
            return true;
        }
        if ("否".equals(value)) {
            return false;
        }
        return null;
    }

    private static boolean invalidCnBool(String text) {
        String value = text == null ? "" : text.trim();
        return !value.isEmpty() && !"是".equals(value) && !"否".equals(value);
    }

    /** 面向人的约束描述 → constraints（正则本期不支持 CSV 表达，登记为已知限制） */
    private static SyncConfigValidator.Outcome<Map<String, Object>> parseConstraintsText(String valueType, String text) {
        if (text == null || text.isEmpty()) {
            return SyncConfigValidator.Outcome.ok(null);
        }
        if ("NUMBER".equals(valueType)) {
            Matcher hit = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)(\\s*的整数)?$").matcher(text);
            if (!hit.matches()) {
                return SyncConfigValidator.Outcome.err(
                        "取值范围「" + text + "」无法解析，须写成「0-10」或「0-10 的整数」");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("min", Integer.valueOf(hit.group(1)));
            value.put("max", Integer.valueOf(hit.group(2)));
            value.put("step", 1);
            value.put("integerOnly", hit.group(3) != null);
            value.put("precision", 0);
            return SyncConfigValidator.Outcome.ok(value);
        }
        if ("TEXT".equals(valueType)) {
            Matcher hit = Pattern.compile("^(\\d+)(?:\\s*-\\s*(\\d+))?\\s*字符$").matcher(text);
            if (!hit.matches()) {
                return SyncConfigValidator.Outcome.err(
                        "长度约束「" + text + "」无法解析，须写成「20 字符」或「1-50 字符」");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("minLen", Integer.valueOf(hit.group(1)));
            value.put("maxLen", hit.group(2) != null ? Integer.valueOf(hit.group(2)) : Integer.valueOf(hit.group(1)));
            value.put("pattern", null);
            value.put("patternHint", null);
            return SyncConfigValidator.Outcome.ok(value);
        }
        return SyncConfigValidator.Outcome.ok(null);
    }

    private static ExtraParse parseExtraAttrs(String setKey, String text) {
        if (text == null || text.isEmpty()) {
            return new ExtraParse(null, List.of(), null);
        }
        List<String> known = switch (setKey) {
            case SyncConstants.SET_COLLECT_FREQUENCY -> List.of("intervalMinutes");
            case SyncConstants.SET_TIME_TEMPLATE -> List.of("startTime", "endTime");
            default -> List.of();
        };
        Map<String, Object> attrs = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String pair : text.split(";")) {
            int eq = pair.indexOf('=');
            String name = (eq < 0 ? pair : pair.substring(0, eq)).trim();
            if (name.isEmpty()) {
                continue;
            }
            String value = eq < 0 ? "" : pair.substring(eq + 1).trim();
            if (!known.contains(name)) {
                warnings.add("附加属性「" + name + "」不属于选项集「" + setKey + "」，已忽略");
                continue;
            }
            attrs.put(name, SyncConstants.SET_COLLECT_FREQUENCY.equals(setKey) ? parseNumberOrNull(value) : value);
        }
        if (SyncConstants.SET_COLLECT_FREQUENCY.equals(setKey)) {
            Object minutes = attrs.get("intervalMinutes");
            if (!(minutes instanceof Number number) || number.doubleValue() <= 0) {
                return new ExtraParse(null, warnings, "附加属性须包含 intervalMinutes（分钟数，大于 0）");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("intervalMinutes", number);
            return new ExtraParse(value, warnings, null);
        }
        if (SyncConstants.SET_TIME_TEMPLATE.equals(setKey)) {
            String start = attrs.get("startTime") == null ? null : String.valueOf(attrs.get("startTime"));
            String end = attrs.get("endTime") == null ? null : String.valueOf(attrs.get("endTime"));
            if (!SyncClock.isClock(start) || !SyncClock.isEndClock(end)) {
                return new ExtraParse(null, warnings, "附加属性须为 startTime=HH:mm;endTime=HH:mm");
            }
            if (SyncClock.minutesOfClock(start) >= SyncClock.minutesOfClock(end)) {
                return new ExtraParse(null, warnings, "时段模板的结束时间须晚于开始时间");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("startTime", start);
            value.put("endTime", end);
            return new ExtraParse(value, warnings, null);
        }
        return new ExtraParse(null, warnings, null);
    }

    private static Number parseNumberOrNull(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
