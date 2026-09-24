package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SyncConfigCatalog} 的内存实现：由 Service 从库中一次性装配，供导入/导出/校验复用。
 * <p>
 * 为什么用内存快照：配置中心单次请求的数据量极小（5 个内置项 + 十余个选项 + 8 驿站覆盖），
 * 装配一次快照可避免导入 1000 行时逐行查库（N+1），同时让纯逻辑可在单测中直接构造消费。
 */
@Data
@RequiredArgsConstructor
public class SyncConfigCatalogSnapshot implements SyncConfigCatalog {

    private final List<SyncConfigItem> items;
    private final List<SyncOptionSet> optionSets;
    private final Map<String, Object> globalValues;
    private final Map<Long, Map<String, Object>> overrides;
    private final Map<Long, String> stationNames;
    private final Map<String, Long> stationIdIndex;

    @Override
    public List<SyncConfigItem> items() {
        return items;
    }

    @Override
    public SyncConfigItem findItem(String itemKey) {
        if (itemKey == null) {
            return null;
        }
        for (SyncConfigItem item : items) {
            if (itemKey.equals(item.getItemKey())) {
                return item;
            }
        }
        return null;
    }

    @Override
    public List<SyncOptionSet> optionSets() {
        return optionSets;
    }

    @Override
    public SyncOptionSet findOptionSet(String setKey) {
        if (setKey == null) {
            return null;
        }
        for (SyncOptionSet set : optionSets) {
            if (setKey.equals(set.getSetKey())) {
                return set;
            }
        }
        return null;
    }

    @Override
    public List<SyncConfigOption> optionsOfSet(String setKey) {
        SyncOptionSet set = findOptionSet(setKey);
        // 集合停用 = 无可选项（编码 validateSingleSelect 的 set.enabled 判定）
        return set == null || !set.isEnabled() ? List.of() : set.getOptions();
    }

    @Override
    public Map<String, Object> globalValues() {
        return globalValues == null ? Map.of() : globalValues;
    }

    @Override
    public Set<String> overrideKeys(Long stationId) {
        Map<String, Object> row = overrides == null ? null : overrides.get(stationId);
        return row == null ? Set.of() : row.keySet();
    }

    @Override
    public Map<String, Object> overridesOf(Long stationId) {
        Map<String, Object> row = overrides == null ? null : overrides.get(stationId);
        return row == null ? Map.of() : row;
    }

    @Override
    public Long stationIdByName(String stationName) {
        return stationIdIndex == null ? null : stationIdIndex.get(stationName);
    }

    @Override
    public String stationName(Long stationId) {
        return stationNames == null ? null : stationNames.get(stationId);
    }

    @Override
    public List<Long> stationIds() {
        return stationNames == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(stationNames.keySet()));
    }

    /** 便捷构造：仅装填导入/导出所需的最小字段（单测用） */
    public static SyncConfigCatalogSnapshot of(List<SyncConfigItem> items, List<SyncOptionSet> optionSets,
                                               Map<String, Object> globalValues,
                                               Map<Long, Map<String, Object>> overrides,
                                               Map<Long, String> stationNames) {
        Map<String, Long> index = new LinkedHashMap<>();
        if (stationNames != null) {
            stationNames.forEach((id, name) -> index.put(name, id));
        }
        return new SyncConfigCatalogSnapshot(items, optionSets,
                globalValues == null ? new LinkedHashMap<>() : globalValues,
                overrides == null ? new LinkedHashMap<>() : overrides,
                stationNames == null ? new LinkedHashMap<>() : stationNames, index);
    }
}
