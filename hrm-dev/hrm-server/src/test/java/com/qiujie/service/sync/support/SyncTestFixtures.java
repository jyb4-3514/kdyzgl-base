package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步配置中心测试夹具（仅测试期使用）：构造与 Mock 种子等价的只读快照。
 * <p>
 * 与 {@code SyncConfigSeeder} 的种子同构（5 内置项 / 3 选项集 / 全局默认 / 2 驿站覆盖），
 * 使纯逻辑单测无需 DB 即可覆盖四层模型的读写分支。
 */
final class SyncTestFixtures {

    private SyncTestFixtures() {
    }

    static SyncConfigItem item(String itemKey, String name, String valueType, int required, String defaultValue,
                               String optionSetKey, Map<String, Object> constraints) {
        SyncConfigItem item = new SyncConfigItem();
        item.setItemKey(itemKey);
        item.setName(name);
        item.setValueType(valueType);
        item.setRequired(required);
        item.setDefaultValue(defaultValue);
        item.setOptionSetKey(optionSetKey);
        item.setScope("STATION");
        item.setEnabled(1);
        item.setBuiltin(1);
        item.setConstraints(constraints);
        return item;
    }

    static SyncConfigOption option(String setKey, String optionKey, String label, boolean builtin, boolean enabled) {
        SyncConfigOption option = new SyncConfigOption();
        option.setSetKey(setKey);
        option.setOptionKey(optionKey);
        option.setLabel(label);
        option.setEnabled(enabled ? 1 : 0);
        option.setBuiltin(builtin ? 1 : 0);
        option.setSource(builtin ? "BUILTIN" : "MANUAL");
        option.setSort(10);
        return option;
    }

    static SyncOptionSet set(String setKey, String name, List<SyncConfigOption> options) {
        return new SyncOptionSet(setKey, name, name, true, true, new ArrayList<>(options));
    }

    static List<SyncConfigItem> items() {
        Map<String, Object> numberRange = new LinkedHashMap<>();
        numberRange.put("min", 0);
        numberRange.put("max", 10);
        numberRange.put("step", 1);
        numberRange.put("integerOnly", true);
        numberRange.put("precision", 0);
        // timeout_minutes 独立约束：对齐 Mock 种子 syncConfigStore.js:111 { min:1, max:120, ... }，
        // 原夹具误与 retry_times 共用 0-10，导致默认值 30 越界 → CSV 导出即导入有 2 行（ITEM/GLOBAL）校验失败
        Map<String, Object> minuteRange = new LinkedHashMap<>();
        minuteRange.put("min", 1);
        minuteRange.put("max", 120);
        minuteRange.put("step", 1);
        minuteRange.put("integerOnly", true);
        minuteRange.put("precision", 0);
        List<SyncConfigItem> items = new ArrayList<>();
        items.add(item("data_source", "数据源", "SINGLE_SELECT", 1, null, "data_source", null));
        items.add(item("collect_frequency", "采集频率", "SINGLE_SELECT", 1, "EVERY_240M", "collect_frequency", null));
        items.add(item("time_template", "采集时段模板", "SINGLE_SELECT", 0, "WORKDAY", "time_template", null));
        items.add(item("retry_times", "重试次数", "NUMBER", 1, "3", null, numberRange));
        items.add(item("timeout_minutes", "超时时长", "NUMBER", 1, "30", null, minuteRange));
        return items;
    }

    static List<SyncOptionSet> optionSets() {
        List<SyncOptionSet> sets = new ArrayList<>();
        sets.add(set("data_source", "数据源", List.of(
                option("data_source", "DUODUOCAI", "多多买菜", true, true),
                option("data_source", "CAINIAO", "菜鸟裹裹", true, true),
                option("data_source", "JD", "京东物流", true, true))));
        sets.add(set("collect_frequency", "采集频率", List.of(
                option("collect_frequency", "EVERY_120M", "每 2 小时", true, true),
                option("collect_frequency", "EVERY_240M", "每 4 小时", true, true))));
        sets.add(set("time_template", "采集时段模板", List.of(
                option("time_template", "WORKDAY", "营业时段 08:00-20:00", true, true))));
        return sets;
    }

    static Map<String, Object> globals() {
        Map<String, Object> globals = new LinkedHashMap<>();
        globals.put("data_source", null);
        globals.put("collect_frequency", "EVERY_240M");
        globals.put("time_template", "WORKDAY");
        globals.put("retry_times", 3);
        globals.put("timeout_minutes", 30);
        return globals;
    }

    static Map<Long, String> stations() {
        Map<Long, String> stations = new LinkedHashMap<>();
        stations.put(1L, "城东驿站");
        stations.put(2L, "城西驿站");
        return stations;
    }

    static Map<Long, Map<String, Object>> overrides() {
        Map<Long, Map<String, Object>> overrides = new LinkedHashMap<>();
        Map<String, Object> stationOne = new LinkedHashMap<>();
        stationOne.put("data_source", "DUODUOCAI");
        stationOne.put("collect_frequency", "EVERY_120M");
        overrides.put(1L, stationOne);
        Map<String, Object> stationTwo = new LinkedHashMap<>();
        stationTwo.put("retry_times", 5);
        overrides.put(2L, stationTwo);
        return overrides;
    }

    static SyncConfigCatalogSnapshot catalog() {
        return SyncConfigCatalogSnapshot.of(items(), optionSets(), globals(), overrides(), stations());
    }
}
