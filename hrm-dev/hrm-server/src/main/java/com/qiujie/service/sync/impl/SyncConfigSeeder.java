package com.qiujie.service.sync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.entity.Station;
import com.qiujie.entity.SyncConfigGlobal;
import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import com.qiujie.entity.SyncConfigStationOverride;
import com.qiujie.entity.SyncStationConfig;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.SyncConfigGlobalMapper;
import com.qiujie.mapper.SyncConfigItemMapper;
import com.qiujie.mapper.SyncConfigOptionMapper;
import com.qiujie.mapper.SyncConfigStationOverrideMapper;
import com.qiujie.mapper.SyncStationConfigMapper;
import com.qiujie.service.sync.support.SyncConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步配置中心内建数据播种（幂等）。
 * <p>
 * <b>为什么需要播种</b>：{@code V12__sync_config_center.sql} 只建表不含数据（数据播种不属表结构变更，亦无种子迁移），
 * 而契约要求配置中心始终存在 5 个内置配置项 + 3 个选项集 + 全局默认；缺这段种子，四层模型与前端「配置项与选项集」
 * 页将整体为空。本播种器对齐 Mock {@code db.js#SYNC_CONFIG_SEED} / {@code syncConfigStore.js#seed + migrateLegacyValues}。
 * <p>
 * <b>为什么不写进 Flyway</b>：种子数据（内建项/默认值/演示样本）属可随版本演进的业务默认值，且执行迁移属 C 档；
 * 采用幂等 ApplicationRunner 可在每次启动补齐缺失项，且不阻塞「空库可用」。
 * TODO(扩展): 若主智能体/数据库工程师认为种子应固化进迁移，请以独立 V 版本落 INSERT（本类届时改为只读兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncConfigSeeder implements ApplicationRunner {

    private final SyncConfigItemMapper itemMapper;
    private final SyncConfigOptionMapper optionMapper;
    private final SyncConfigGlobalMapper globalMapper;
    private final SyncStationConfigMapper configMapper;
    private final SyncConfigStationOverrideMapper overrideMapper;
    private final StationMapper stationMapper;

    /** 内建配置项（对齐 Mock ITEM_SEED，顺序即 sort） */
    private static final Object[][] ITEM_SEED = {
            {"data_source", "数据源", "包裹采集的数据来源", "SINGLE_SELECT", 1, null, "", null, "data_source", "STATION", 10, 1, 1},
            {"collect_frequency", "采集频率", "两次采集的最小间隔", "SINGLE_SELECT", 1, "EVERY_240M", "", null, "collect_frequency", "STATION", 20, 1, 1},
            {"time_template", "采集时段模板", "预置采集时段模板", "SINGLE_SELECT", 0, "WORKDAY", "", null, "time_template", "STATION", 30, 1, 1},
            {"retry_times", "重试次数", "批次失败后的最大自动重试次数", "NUMBER", 1, "3", "次", "min:0,max:10,step:1,integerOnly:true,precision:0", null, "STATION", 40, 1, 1},
            {"timeout_minutes", "超时时长", "单个批次超过该时长判定为超时", "NUMBER", 1, "30", "分钟", "min:1,max:120,step:1,integerOnly:true,precision:0", null, "STATION", 50, 1, 1}
    };

    /** 选项集种子：[setKey, optionKey, label, intervalMinutes, startTime, endTime, sort, enabled, builtin, source, legacyCodes(逗号分隔), remark] */
    private static final Object[][] OPTION_SEED = {
            {"data_source", "DUODUOCAI", "多多买菜", null, null, null, 10, 1, 1, "BUILTIN", "多多买菜", null},
            {"data_source", "CAINIAO", "菜鸟裹裹", null, null, null, 20, 1, 1, "BUILTIN", "菜鸟裹裹", null},
            {"data_source", "JD", "京东物流", null, null, null, 30, 1, 1, "BUILTIN", "京东物流", null},
            {"data_source", "SF", "顺丰速运", null, null, null, 40, 1, 1, "BUILTIN", "", null},
            {"collect_frequency", "EVERY_30M", "每 30 分钟", 30, null, null, 10, 1, 0, "MANUAL", "", "由管理员新增的档位示例"},
            {"collect_frequency", "EVERY_60M", "每小时", 60, null, null, 20, 1, 1, "BUILTIN", "HOURLY", null},
            {"collect_frequency", "EVERY_120M", "每 2 小时", 120, null, null, 30, 1, 1, "BUILTIN", "EVERY_2H", null},
            {"collect_frequency", "EVERY_240M", "每 4 小时", 240, null, null, 40, 1, 1, "BUILTIN", "EVERY_4H", null},
            {"collect_frequency", "EVERY_1440M", "每天", 1440, null, null, 50, 1, 1, "BUILTIN", "DAILY", null},
            {"time_template", "WORKDAY", "营业时段 08:00-20:00", null, "08:00", "20:00", 10, 1, 1, "BUILTIN", "", null},
            {"time_template", "ALLDAY", "全天 08:00-24:00", null, "08:00", "24:00", 20, 1, 1, "BUILTIN", "", null}
    };

    /** 全局默认（对齐 Mock GLOBAL_SEED；data_source 留空 = 未配置态） */
    private static final Map<String, String> GLOBAL_SEED = new LinkedHashMap<>();

    static {
        GLOBAL_SEED.put("data_source", null);
        GLOBAL_SEED.put("collect_frequency", "EVERY_240M");
        GLOBAL_SEED.put("time_template", "WORKDAY");
        GLOBAL_SEED.put("retry_times", "3");
        GLOBAL_SEED.put("timeout_minutes", "30");
    }

    /** 演示用驿站采集配置样本（对齐 Mock db.js SYNC_CONFIG_SEED，仅当对应驿站存在时播种） */
    private static final Object[][] STATION_SEED = {
            {1, 1, "HOURLY", "多多买菜", "SUCCESS", 1, "EVERY_60M"},
            {2, 1, "EVERY_2H", "菜鸟裹裹", "SUCCESS", 1, "EVERY_120M"},
            {3, 1, "EVERY_4H", "多多买菜", "SUCCESS", 1, "EVERY_240M"},
            {4, 1, "DAILY", "京东物流", "SUCCESS", 1, "EVERY_1440M"},
            {5, 1, "EVERY_2H", "多多买菜", "FAILED", 1, "EVERY_120M"},
            {6, 1, "HOURLY", "丰巢智能柜", "FAILED", 1, "EVERY_60M"},
            {7, 0, "EVERY_4H", null, "NEVER", 1, "EVERY_240M"},
            {8, 0, "DAILY", null, "NEVER", 0, "EVERY_1440M"}
    };

    /** 覆盖样本：2 号驿站重试 5 次（对齐 Mock EXTRA_OVERRIDE_SEED） */
    private static final Map<Long, String[]> EXTRA_OVERRIDE = Map.of(2L, new String[]{"retry_times", "5"});

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            seedItems();
            seedOptions();
            seedGlobals();
            seedStationsAndOverrides();
        } catch (RuntimeException e) {
            // 播种失败不应阻断应用启动：记录告警，交由运维/后续请求兜底
            log.warn("同步配置中心内建种子播种失败（不影响启动）：{}", e.getMessage(), e);
        }
    }

    private void seedItems() {
        if (count(itemMapper.selectCount(new LambdaQueryWrapper<SyncConfigItem>())) > 0) {
            return;
        }
        for (Object[] row : ITEM_SEED) {
            SyncConfigItem item = new SyncConfigItem();
            item.setItemKey((String) row[0]);
            item.setName((String) row[1]);
            item.setDescription((String) row[2]);
            item.setValueType((String) row[3]);
            item.setRequired((Integer) row[4]);
            item.setDefaultValue((String) row[5]);
            item.setUnit((String) row[6]);
            item.setConstraints(parseConstraints((String) row[7]));
            item.setOptionSetKey((String) row[8]);
            item.setScope((String) row[9]);
            item.setSort((Integer) row[10]);
            item.setEnabled((Integer) row[11]);
            item.setBuiltin((Integer) row[12]);
            itemMapper.insert(item);
        }
        log.info("同步配置中心：已播种 {} 个内建配置项", ITEM_SEED.length);
    }

    private void seedOptions() {
        if (count(optionMapper.selectCount(new LambdaQueryWrapper<SyncConfigOption>())) > 0) {
            return;
        }
        for (Object[] row : OPTION_SEED) {
            SyncConfigOption option = new SyncConfigOption();
            option.setSetKey((String) row[0]);
            option.setOptionKey((String) row[1]);
            option.setLabel((String) row[2]);
            option.setExtraAttrs(parseExtraAttrs((String) row[0], row[3], row[4], row[5]));
            option.setSort((Integer) row[6]);
            option.setEnabled((Integer) row[7]);
            option.setBuiltin((Integer) row[8]);
            option.setSource((String) row[9]);
            option.setLegacyCodes(parseLegacyCodes((String) row[10]));
            option.setRemark((String) row[11]);
            optionMapper.insert(option);
        }
        log.info("同步配置中心：已播种 {} 个选项", OPTION_SEED.length);
    }

    private void seedGlobals() {
        if (count(globalMapper.selectCount(new LambdaQueryWrapper<SyncConfigGlobal>())) > 0) {
            return;
        }
        GLOBAL_SEED.forEach((itemKey, value) -> {
            SyncConfigGlobal row = new SyncConfigGlobal();
            row.setItemKey(itemKey);
            row.setValue(value);
            globalMapper.insert(row);
        });
        log.info("同步配置中心：已播种 {} 个全局默认值", GLOBAL_SEED.size());
    }

    private void seedStationsAndOverrides() {
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>().orderByAsc(Station::getId));
        if (stations.isEmpty()) {
            return;
        }
        Map<Long, Station> stationById = new LinkedHashMap<>();
        stations.forEach(station -> stationById.put(station.getId(), station));
        LocalDateTime now = LocalDateTime.now();
        for (Object[] row : STATION_SEED) {
            Long stationId = ((Integer) row[0]).longValue();
            if (!stationById.containsKey(stationId)) {
                continue;
            }
            boolean fresh = count(configMapper.selectCount(new LambdaQueryWrapper<SyncStationConfig>()
                    .eq(SyncStationConfig::getStationId, stationId))) == 0;
            String lastStatus = (String) row[4];
            if (!fresh) {
                continue;
            }
            SyncStationConfig config = new SyncStationConfig();
            config.setStationId(stationId);
            config.setEnabled((Integer) row[1]);
            config.setFrequency((String) row[6]);
            config.setDataSource(resolveDataSourceOptionKey((String) row[3]));
            config.setCollectStartTime("08:00");
            config.setCollectEndTime("20:00");
            config.setLastCollectStatus(lastStatus);
            config.setLastCollectTime(SyncConstants.COLLECT_NEVER.equals(lastStatus) ? null : now.minusHours(6 + stationId));
            config.setStatus((Integer) row[5]);
            configMapper.insert(config);
            seedOverrides(stationId, (String) row[6], (String) row[3]);
        }
    }

    private void seedOverrides(Long stationId, String frequencyKey, String dataSourceText) {
        if (count(overrideMapper.selectCount(new LambdaQueryWrapper<SyncConfigStationOverride>()
                .eq(SyncConfigStationOverride::getStationId, stationId))) > 0) {
            return;
        }
        // 与新全局默认一致的项不登记覆盖（视为继承）
        if (frequencyKey != null && !frequencyKey.equals(GLOBAL_SEED.get("collect_frequency"))) {
            insertOverride(stationId, "collect_frequency", frequencyKey);
        }
        String dataSourceKey = resolveDataSourceOptionKey(dataSourceText);
        if (dataSourceKey != null) {
            insertOverride(stationId, "data_source", dataSourceKey);
        }
        String[] extra = EXTRA_OVERRIDE.get(stationId);
        if (extra != null) {
            insertOverride(stationId, extra[0], extra[1]);
        }
    }

    private void insertOverride(Long stationId, String itemKey, String value) {
        SyncConfigStationOverride row = new SyncConfigStationOverride();
        row.setStationId(stationId);
        row.setItemKey(itemKey);
        row.setValue(value);
        overrideMapper.insert(row);
    }

    /** 数据源文本 → 选项 Key（未命中受管选项时自动纳管为 MIGRATED，不置空） */
    private String resolveDataSourceOptionKey(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<SyncConfigOption> options = optionMapper.selectList(new LambdaQueryWrapper<SyncConfigOption>()
                .eq(SyncConfigOption::getSetKey, SyncConstants.SET_DATA_SOURCE));
        for (SyncConfigOption option : options) {
            if (text.equals(option.getOptionKey())
                    || (option.getLegacyCodes() != null && option.getLegacyCodes().contains(text))) {
                return option.getOptionKey();
            }
        }
        int seq = 1;
        int maxSort = 0;
        for (SyncConfigOption option : options) {
            if (option.getOptionKey() != null && option.getOptionKey().startsWith("MIGRATED_")) {
                seq++;
            }
            maxSort = Math.max(maxSort, option.getSort() == null ? 0 : option.getSort());
        }
        SyncConfigOption created = new SyncConfigOption();
        created.setSetKey(SyncConstants.SET_DATA_SOURCE);
        created.setOptionKey("MIGRATED_" + seq);
        created.setLabel(text.trim());
        created.setSort(maxSort + 10);
        created.setEnabled(1);
        created.setBuiltin(0);
        created.setSource("MIGRATED");
        created.setLegacyCodes(List.of(text.trim()));
        created.setRemark("由历史配置自动创建");
        optionMapper.insert(created);
        return created.getOptionKey();
    }

    // ==================== 解析小工具 ====================

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    /** "min:0,max:10,step:1,integerOnly:true,precision:0" → Map（NUMBER 约束） */
    private Map<String, Object> parseConstraints(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String pair : text.split(",")) {
            String[] kv = pair.split(":", 2);
            String key = kv[0].trim();
            String value = kv.length > 1 ? kv[1].trim() : "";
            map.put(key, "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                    ? Boolean.valueOf(value) : Integer.valueOf(value));
        }
        return map;
    }

    /** 选项附加属性（按选项集约定） */
    private Map<String, Object> parseExtraAttrs(String setKey, Object intervalMinutes, Object startTime, Object endTime) {
        if (SyncConstants.SET_COLLECT_FREQUENCY.equals(setKey) && intervalMinutes != null) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("intervalMinutes", intervalMinutes);
            return attrs;
        }
        if (SyncConstants.SET_TIME_TEMPLATE.equals(setKey) && startTime != null && endTime != null) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("startTime", startTime);
            attrs.put("endTime", endTime);
            return attrs;
        }
        return null;
    }

    private List<String> parseLegacyCodes(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(text.split(","));
    }
}
