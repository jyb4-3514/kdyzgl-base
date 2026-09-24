package com.qiujie.service.sync.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.entity.Station;
import com.qiujie.entity.SyncConfigGlobal;
import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import com.qiujie.entity.SyncConfigStationOverride;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.SyncConfigGlobalMapper;
import com.qiujie.mapper.SyncConfigItemMapper;
import com.qiujie.mapper.SyncConfigOptionMapper;
import com.qiujie.mapper.SyncConfigStationOverrideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置中心四层数据读写（配置项定义 / 选项集 / 全局默认 / 驿站覆盖）。
 * <p>
 * 为什么单列一个 store（对齐 Mock {@code syncConfigStore.js}）：四层数据的读写与校验被「驿站采集配置」
 * （运行态视角）与「配置中心」（元数据视角）两个 Service 共用；若各自维护一份，必然出现两套合并/校验口径。
 * <p>
 * 本类只做「四层数据 + 校验 + 引用查询」，不感知 HTTP 形态（那是 Controller / Service 的事），
 * 也不构建 CSV（那是 {@code SyncCsv*} 的事）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncConfigStore {

    private final SyncConfigItemMapper itemMapper;
    private final SyncConfigOptionMapper optionMapper;
    private final SyncConfigGlobalMapper globalMapper;
    private final SyncConfigStationOverrideMapper overrideMapper;
    private final StationMapper stationMapper;

    // ==================== 读：配置项 / 选项集 ====================

    /** 全部启用态配置项（按 sort 升序；含停用项——列表页需展示并可重新启用） */
    public List<SyncConfigItem> items() {
        return itemMapper.selectList(new LambdaQueryWrapper<SyncConfigItem>()
                .orderByAsc(SyncConfigItem::getSort).orderByAsc(SyncConfigItem::getId));
    }

    /** 按 Key 取配置项（不存在返回 null） */
    public SyncConfigItem findItem(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return null;
        }
        List<SyncConfigItem> rows = itemMapper.selectList(new LambdaQueryWrapper<SyncConfigItem>()
                .eq(SyncConfigItem::getItemKey, itemKey).orderByAsc(SyncConfigItem::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 引用某选项集的配置项（选项的「归属方」） */
    public List<SyncConfigItem> itemsOfSet(String setKey) {
        if (setKey == null) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<SyncConfigItem>()
                .eq(SyncConfigItem::getOptionSetKey, setKey));
    }

    /** 全部选项集（集合层元数据来自常量，选项按 sort 升序） */
    public List<SyncOptionSet> optionSets() {
        List<SyncConfigOption> options = optionMapper.selectList(new LambdaQueryWrapper<SyncConfigOption>()
                .orderByAsc(SyncConfigOption::getSetKey).orderByAsc(SyncConfigOption::getSort)
                .orderByAsc(SyncConfigOption::getId));
        Map<String, SyncOptionSet> grouped = new LinkedHashMap<>();
        // 先按常量登记集合层元数据：即使集合内暂无选项，也要能被 createItem 的「选项集是否存在」校验识别
        for (String[] meta : SyncConstants.OPTION_SET_META) {
            grouped.put(meta[0], new SyncOptionSet(meta[0], meta[1], meta[2], true, true, new ArrayList<>()));
        }
        for (SyncConfigOption option : options) {
            SyncOptionSet set = grouped.computeIfAbsent(option.getSetKey(),
                    key -> new SyncOptionSet(key, SyncConstants.optionSetName(key),
                            SyncConstants.optionSetDescription(key), SyncConstants.isBuiltinSet(key), true,
                            new ArrayList<>()));
            set.getOptions().add(option);
        }
        return new ArrayList<>(grouped.values());
    }

    /** 按 setKey 取选项集（不存在返回 null） */
    public SyncOptionSet findOptionSet(String setKey) {
        if (setKey == null) {
            return null;
        }
        for (SyncOptionSet set : optionSets()) {
            if (setKey.equals(set.getSetKey())) {
                return set;
            }
        }
        return null;
    }

    /** 某配置项关联选项集的可选项：集合停用或不存在时返回空列表（编码「暂无可选项」语义） */
    public List<SyncConfigOption> optionsOfSet(String setKey) {
        SyncOptionSet set = findOptionSet(setKey);
        return set == null || !set.isEnabled() ? List.of() : set.getOptions();
    }

    // ==================== 读：全局默认 / 驿站覆盖 ====================

    /** 全局默认（类型化，按 itemKey 索引；无库行时回落到配置项默认值） */
    public Map<String, Object> globalValues() {
        List<SyncConfigGlobal> rows = globalMapper.selectList(new LambdaQueryWrapper<SyncConfigGlobal>());
        Map<String, SyncConfigGlobal> byKey = new LinkedHashMap<>();
        rows.forEach(row -> byKey.put(row.getItemKey(), row));
        Map<String, Object> values = new LinkedHashMap<>();
        for (SyncConfigItem item : items()) {
            SyncConfigGlobal row = byKey.get(item.getItemKey());
            if (row != null) {
                values.put(item.getItemKey(), SyncValueCodec.toTyped(item.getValueType(), row.getValue()));
            } else {
                // 无全局行 = 未配置：回落到配置项默认值（data_source 默认 null，故保持「未配置」态）
                values.put(item.getItemKey(), SyncValueCodec.toTyped(item.getValueType(), item.getDefaultValue()));
            }
        }
        return values;
    }

    /** 某驿站的覆盖值（类型化） */
    public Map<String, Object> overridesOf(Long stationId) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (stationId == null) {
            return overrides;
        }
        Map<String, SyncConfigItem> itemsByKey = itemsByKey();
        List<SyncConfigStationOverride> rows = overrideMapper.selectList(
                new LambdaQueryWrapper<SyncConfigStationOverride>().eq(SyncConfigStationOverride::getStationId, stationId));
        for (SyncConfigStationOverride row : rows) {
            SyncConfigItem item = itemsByKey.get(row.getItemKey());
            String valueType = item == null ? null : item.getValueType();
            overrides.put(row.getItemKey(), SyncValueCodec.toTyped(valueType, row.getValue()));
        }
        return overrides;
    }

    /** 某驿站的覆盖键集合 */
    public Set<String> overrideKeys(Long stationId) {
        return new LinkedHashSet<>(overridesOf(stationId).keySet());
    }

    /** 某配置项在该站的生效值（覆盖优先，否则全局默认） */
    public Object overridesOrGlobal(Long stationId, String itemKey) {
        Map<String, Object> overrides = overridesOf(stationId);
        if (overrides.containsKey(itemKey)) {
            return overrides.get(itemKey);
        }
        return globalValues().get(itemKey);
    }

    // ==================== 读：引用查询（影响面） ====================

    /** 覆盖了某配置项的驿站 id（升序） */
    public List<Long> stationsReferencingItem(String itemKey) {
        List<SyncConfigStationOverride> rows = overrideMapper.selectList(
                new LambdaQueryWrapper<SyncConfigStationOverride>().eq(SyncConfigStationOverride::getItemKey, itemKey));
        return distinctStationIds(rows);
    }

    /** 覆盖值指向某选项的驿站 id（升序；先取引用该选项集的配置项，再按值匹配） */
    public List<Long> stationsReferencingOption(String setKey, String optionKey) {
        List<SyncConfigItem> owners = itemsOfSet(setKey);
        if (owners.isEmpty()) {
            return List.of();
        }
        List<String> ownerKeys = new ArrayList<>();
        owners.forEach(item -> ownerKeys.add(item.getItemKey()));
        List<SyncConfigStationOverride> rows = overrideMapper.selectList(
                new LambdaQueryWrapper<SyncConfigStationOverride>().in(SyncConfigStationOverride::getItemKey, ownerKeys));
        List<SyncConfigStationOverride> matched = new ArrayList<>();
        for (SyncConfigStationOverride row : rows) {
            if (optionKey.equals(row.getValue())) {
                matched.add(row);
            }
        }
        return distinctStationIds(matched);
    }

    /** 某选项是否正被作为全局默认值使用 */
    public boolean optionInGlobal(String setKey, String optionKey) {
        Map<String, Object> global = globalValues();
        for (SyncConfigItem owner : itemsOfSet(setKey)) {
            Object value = global.get(owner.getItemKey());
            if (optionKey.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 引用该选项集的配置项名称（全局默认阻断文案用） */
    public List<String> ownerNames(String setKey) {
        List<String> names = new ArrayList<>();
        for (SyncConfigItem item : itemsOfSet(setKey)) {
            names.add(item.getName());
        }
        return names;
    }

    // ==================== 读：驿站 ====================

    /** 全部驿站 id → 名称（升序） */
    public Map<Long, String> stationNames() {
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .select(Station::getId, Station::getStationName).orderByAsc(Station::getId));
        Map<Long, String> map = new LinkedHashMap<>();
        for (Station station : stations) {
            map.put(station.getId(), station.getStationName());
        }
        return map;
    }

    /** 校验驿站是否存在 */
    public boolean stationExists(Long stationId) {
        return stationId != null && stationMapper.selectById(stationId) != null;
    }

    /** 只读快照（导入 / 导出 / 校验共用） */
    public SyncConfigCatalogSnapshot catalog() {
        List<SyncConfigItem> items = items();
        Map<String, Object> global = globalValues();
        Map<Long, Map<String, Object>> overrides = new LinkedHashMap<>();
        Map<Long, String> stationNames = stationNames();
        Map<String, SyncConfigItem> itemsByKey = new LinkedHashMap<>();
        items.forEach(item -> itemsByKey.put(item.getItemKey(), item));
        List<SyncConfigStationOverride> rows = overrideMapper.selectList(
                new LambdaQueryWrapper<SyncConfigStationOverride>());
        for (SyncConfigStationOverride row : rows) {
            SyncConfigItem item = itemsByKey.get(row.getItemKey());
            Map<String, Object> stationOverrides = overrides.computeIfAbsent(row.getStationId(),
                    key -> new LinkedHashMap<>());
            stationOverrides.put(row.getItemKey(),
                    SyncValueCodec.toTyped(item == null ? null : item.getValueType(), row.getValue()));
        }
        return SyncConfigCatalogSnapshot.of(items, optionSets(), global, overrides, stationNames);
    }

    // ==================== 写：全局默认 ====================

    /**
     * 保存全局默认（局部更新：只提交改动项）。逐项校验配置项存在 / 启用 / 取值合法，任一失败即整体拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveGlobalValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            SyncConfigItem item = findItem(entry.getKey());
            if (item == null) {
                throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS, "配置项「" + entry.getKey() + "」不存在");
            }
            if (!Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getEnabled()))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "「" + item.getName() + "」已停用，不可修改");
            }
            SyncConfigValidator.Error error =
                    SyncConfigValidator.firstError(item, optionsOfSet(item.getOptionSetKey()), entry.getValue());
            if (error != null) {
                throw new BusinessException(error.code() == 9507 ? ErrorCode.SYNC_REQUIRED_EMPTY : ErrorCode.SYNC_VALUE_INVALID,
                        error.message());
            }
            upsertGlobal(item.getItemKey(), SyncValueCodec.toStored(item.getValueType(), entry.getValue()));
        }
    }

    /** 删除某配置项的全局默认行（删除配置项时联动） */
    public void deleteGlobal(String itemKey) {
        globalMapper.delete(new LambdaQueryWrapper<SyncConfigGlobal>().eq(SyncConfigGlobal::getItemKey, itemKey));
    }

    private void upsertGlobal(String itemKey, String stored) {
        List<SyncConfigGlobal> rows = globalMapper.selectList(
                new LambdaQueryWrapper<SyncConfigGlobal>().eq(SyncConfigGlobal::getItemKey, itemKey));
        if (rows.isEmpty()) {
            SyncConfigGlobal row = new SyncConfigGlobal();
            row.setItemKey(itemKey);
            row.setValue(stored);
            globalMapper.insert(row);
            return;
        }
        SyncConfigGlobal row = rows.get(0);
        row.setValue(stored);
        globalMapper.updateById(row);
    }

    // ==================== 写：驿站覆盖 ====================

    /**
     * 保存某驿站的覆盖（overrides）并重置指定键（resetKeys 删除覆盖 → 回退继承全局默认）。
     * <p>
     * 先整体校验、后统一落库：避免前几项已写、后一项校验失败造成半成品配置（对账困难）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setStationOverrides(Long stationId, Map<String, Object> overrides, List<String> resetKeys) {
        Map<String, Object> effectiveOverrides = overrides == null ? Map.of() : overrides;
        // 1) 整体校验
        for (Map.Entry<String, Object> entry : effectiveOverrides.entrySet()) {
            SyncConfigItem item = findItem(entry.getKey());
            if (item == null) {
                throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS, "配置项「" + entry.getKey() + "」不存在");
            }
            if (!"STATION".equals(item.getScope())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "「" + item.getName() + "」为全局配置项，不支持驿站覆盖");
            }
            SyncConfigValidator.Error error =
                    SyncConfigValidator.firstError(item, optionsOfSet(item.getOptionSetKey()), entry.getValue());
            if (error != null) {
                throw new BusinessException(error.code() == 9507 ? ErrorCode.SYNC_REQUIRED_EMPTY : ErrorCode.SYNC_VALUE_INVALID,
                        error.message());
            }
        }
        // 2) 重置键：删除覆盖行（回退继承全局默认）
        if (resetKeys != null) {
            for (String itemKey : resetKeys) {
                overrideMapper.delete(new LambdaQueryWrapper<SyncConfigStationOverride>()
                        .eq(SyncConfigStationOverride::getStationId, stationId)
                        .eq(SyncConfigStationOverride::getItemKey, itemKey));
            }
        }
        // 3) 覆盖键：有则更新、无则新增
        for (Map.Entry<String, Object> entry : effectiveOverrides.entrySet()) {
            SyncConfigItem item = findItem(entry.getKey());
            String stored = SyncValueCodec.toStored(item.getValueType(), entry.getValue());
            List<SyncConfigStationOverride> rows = overrideMapper.selectList(
                    new LambdaQueryWrapper<SyncConfigStationOverride>()
                            .eq(SyncConfigStationOverride::getStationId, stationId)
                            .eq(SyncConfigStationOverride::getItemKey, entry.getKey()));
            if (rows.isEmpty()) {
                SyncConfigStationOverride row = new SyncConfigStationOverride();
                row.setStationId(stationId);
                row.setItemKey(entry.getKey());
                row.setValue(stored);
                overrideMapper.insert(row);
            } else {
                SyncConfigStationOverride row = rows.get(0);
                row.setValue(stored);
                overrideMapper.updateById(row);
            }
        }
    }

    /** 写入单条覆盖（CSV 导入 STATION 行使用；值可为 null，故不能用 Map.of） */
    public void setStationOverride(Long stationId, String itemKey, Object value) {
        Map<String, Object> single = new LinkedHashMap<>();
        single.put(itemKey, value);
        setStationOverrides(stationId, single, List.of());
    }

    /** 清理指向某配置项的全部覆盖（删除配置项时联动，避免悬空覆盖） */
    public void stripOverridesByItemKey(String itemKey) {
        overrideMapper.delete(new LambdaQueryWrapper<SyncConfigStationOverride>()
                .eq(SyncConfigStationOverride::getItemKey, itemKey));
    }

    /** 清理「值等于被删选项」的覆盖键（仅限该选项集的归属配置项，同站其它配置项覆盖不受影响） */
    public void stripOverridesByOption(String setKey, String optionKey) {
        List<SyncConfigItem> owners = itemsOfSet(setKey);
        if (owners.isEmpty()) {
            return;
        }
        List<String> ownerKeys = new ArrayList<>();
        owners.forEach(item -> ownerKeys.add(item.getItemKey()));
        overrideMapper.delete(new LambdaQueryWrapper<SyncConfigStationOverride>()
                .in(SyncConfigStationOverride::getItemKey, ownerKeys)
                .eq(SyncConfigStationOverride::getValue, optionKey));
    }

    // ==================== 内部 ====================

    private Map<String, SyncConfigItem> itemsByKey() {
        Map<String, SyncConfigItem> map = new LinkedHashMap<>();
        items().forEach(item -> map.put(item.getItemKey(), item));
        return map;
    }

    private List<Long> distinctStationIds(List<SyncConfigStationOverride> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(row -> ids.add(row.getStationId()));
        List<Long> sorted = new ArrayList<>(ids);
        sorted.sort(Long::compareTo);
        return sorted;
    }
}
