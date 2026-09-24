package com.qiujie.service.sync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.common.LoginUser;
import com.qiujie.dto.sync.StationSyncConfigSaveRequest;
import com.qiujie.entity.Station;
import com.qiujie.entity.SyncConfigOption;
import com.qiujie.entity.SyncStationConfig;
import com.qiujie.entity.SyncTask;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.SyncConfigOptionMapper;
import com.qiujie.mapper.SyncStationConfigMapper;
import com.qiujie.mapper.SyncTaskMapper;
import com.qiujie.service.sync.StationSyncConfigService;
import com.qiujie.service.sync.support.SyncClock;
import com.qiujie.service.sync.support.SyncConfigMerger;
import com.qiujie.service.sync.support.SyncConfigStore;
import com.qiujie.service.sync.support.SyncConfigValidator;
import com.qiujie.service.sync.support.SyncConstants;
import com.qiujie.service.sync.support.SyncOptionSet;
import com.qiujie.service.sync.support.SyncValueCodec;
import com.qiujie.util.UserContext;
import com.qiujie.vo.sync.SyncLastBatchVO;
import com.qiujie.vo.sync.SyncOverviewCountsVO;
import com.qiujie.vo.sync.SyncOverviewVO;
import com.qiujie.vo.sync.SyncStationConfigVO;
import com.qiujie.vo.sync.SyncStationStateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驿站采集配置服务实现（M9，4 接口，Mock {@code routes/syncConfig.js}）。
 * <p>
 * 与「批次流水」（{@code /sync-tasks}）分离：本模块是「驿站维度的采集配置 + 状态看板」。
 * 配置值已迁到配置中心四层模型（{@link SyncConfigStore}）：旧字段 frequency / dataSource /
 * collectStartTime / collectEndTime 一律输出「生效值」，保证既有 PC 抽屉与移动端只读状态页零改动可读。
 * <p>
 * 采集状态（collectState）由配置行派生：status=0 → DISABLED；enabled=0 → UNCONFIGURED；
 * lastCollectStatus=FAILED → ABNORMAL；其余 → NORMAL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationSyncConfigServiceImpl implements StationSyncConfigService {

    private static final Map<String, String> COLLECT_STATE_LABELS = Map.of(
            "NORMAL", "正常", "ABNORMAL", "异常", "UNCONFIGURED", "未配置", "DISABLED", "已停用");

    private final SyncStationConfigMapper configMapper;
    private final SyncTaskMapper taskMapper;
    private final StationMapper stationMapper;
    private final SyncConfigOptionMapper optionMapper;
    private final SyncConfigStore store;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public List<SyncStationConfigVO> list() {
        List<SyncStationConfigVO> list = new ArrayList<>();
        for (SyncStationConfig config : scopedConfigs()) {
            list.add(toConfigVO(config));
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public SyncStationConfigVO detail(Long stationId) {
        LoginUser user = currentUser();
        if (!RoleEnum.isAdmin(user.getRole()) && !isOwnStation(stationId, user)) {
            // 跨站按「不存在」返回，避免暴露他人驿站配置的存在性（对齐 Mock detail）
            throw new BusinessException(ErrorCode.NOT_FOUND, "采集配置不存在");
        }
        SyncStationConfig config = findConfig(stationId);
        if (config == null) {
            throw new BusinessException(ErrorCode.SYNC_CONFIG_NOT_EXISTS);
        }
        return toConfigVO(config);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncOverviewVO overview() {
        List<SyncStationConfig> configs = scopedConfigs();
        SyncOverviewCountsVO counts = new SyncOverviewCountsVO();
        List<SyncStationStateVO> stations = new ArrayList<>(configs.size());
        for (SyncStationConfig config : configs) {
            String state = collectStateOf(config);
            switch (state) {
                case "NORMAL" -> counts.setNormal(counts.getNormal() + 1);
                case "ABNORMAL" -> counts.setAbnormal(counts.getAbnormal() + 1);
                case "UNCONFIGURED" -> counts.setUnconfigured(counts.getUnconfigured() + 1);
                default -> counts.setDisabled(counts.getDisabled() + 1);
            }
            SyncStationStateVO row = new SyncStationStateVO();
            row.setStationId(config.getStationId());
            row.setStationName(stationName(config.getStationId()));
            row.setCollectState(state);
            row.setCollectStateLabel(COLLECT_STATE_LABELS.get(state));
            row.setEnabled(Integer.valueOf(1).equals(config.getEnabled()));
            Object dataSource = store.overridesOrGlobal(config.getStationId(), "data_source");
            row.setDataSource(dataSource == null ? null : optionLabelOf(SyncConstants.SET_DATA_SOURCE, String.valueOf(dataSource)));
            row.setLastCollectStatus(config.getLastCollectStatus());
            row.setLastCollectTime(config.getLastCollectTime());
            row.setLastBatch(lastBatchOf(config.getStationId()));
            stations.add(row);
        }
        SyncOverviewVO vo = new SyncOverviewVO();
        vo.setTotal(configs.size());
        vo.setCounts(counts);
        vo.setStations(stations);
        return vo;
    }

    // ==================== 保存 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncStationConfigVO save(Long stationId, StationSyncConfigSaveRequest request) {
        StationSyncConfigSaveRequest req = request == null ? new StationSyncConfigSaveRequest() : request;
        if (!store.stationExists(stationId)) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        SyncStationConfig current = findConfig(stationId);

        Integer enabledVal = normalizeFlag(req.getEnabled(), "enabled");
        Integer statusVal = normalizeFlag(req.getStatus(), "status");
        if (req.getDataSource() != null && !req.getDataSource().isBlank()
                && !textLen(req.getDataSource(), 1, 50)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据源名称长度须为 1-50");
        }

        // 旧字段别名 → overrides / resetKeys / 兼容快照
        // TODO(扩展): 前端全部改用 overrides/resetKeys 后，删除 frequency/dataSource/collectStartTime/collectEndTime
        //   四个旧字段的兼容分支（对齐 Mock syncConfig.js#planLegacyAliases 的同一 TODO）。
        Map<String, Object> overrides = new LinkedHashMap<>();
        List<String> resetKeys = new ArrayList<>();
        Map<String, Object> legacyPatch = new LinkedHashMap<>();
        if (req.getFrequency() != null) {
            String key = resolveOptionKey(SyncConstants.SET_COLLECT_FREQUENCY, req.getFrequency(), true);
            if (key == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "frequency 仅支持选项集内已启用的档位（兼容旧码 HOURLY / EVERY_2H / EVERY_4H / DAILY）");
            }
            overrides.put(SyncConstants.SET_COLLECT_FREQUENCY, key);
        }
        if (req.getDataSource() != null) {
            if (req.getDataSource().isBlank()) {
                resetKeys.add(SyncConstants.SET_DATA_SOURCE);
            } else {
                overrides.put(SyncConstants.SET_DATA_SOURCE, resolveDataSourceKey(req.getDataSource()));
            }
        }
        if (req.getCollectStartTime() != null) {
            if (!SyncClock.isClock(req.getCollectStartTime())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "采集开始时间格式须为 HH:mm");
            }
            legacyPatch.put("collectStartTime", req.getCollectStartTime());
        }
        if (req.getCollectEndTime() != null) {
            if (!SyncClock.isEndClock(req.getCollectEndTime())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "采集结束时间格式须为 HH:mm");
            }
            legacyPatch.put("collectEndTime", req.getCollectEndTime());
        }
        // 起止恰好等于某个时段模板时同步登记模板覆盖，避免「模板」与「起止」两个口径各说各话
        if (legacyPatch.containsKey("collectStartTime") && legacyPatch.containsKey("collectEndTime")) {
            String hit = matchTemplate(String.valueOf(legacyPatch.get("collectStartTime")),
                    String.valueOf(legacyPatch.get("collectEndTime")));
            if (hit != null) {
                overrides.put(SyncConstants.SET_TIME_TEMPLATE, hit);
            }
        }
        if (req.getOverrides() != null) {
            overrides.putAll(req.getOverrides());
        }
        if (req.getResetKeys() != null) {
            resetKeys.addAll(req.getResetKeys());
        }

        Map<String, Object> global = store.globalValues();
        Map<String, Object> planned = SyncConfigMerger.plannedOverrides(store.overridesOf(stationId), overrides, resetKeys);
        int nextEnabled = enabledVal != null ? enabledVal : (current != null && current.getEnabled() != null ? current.getEnabled() : 0);
        // 启用采集前必须先有数据源，否则会落下「开关开着却没有采集来源」的矛盾配置
        if (nextEnabled == 1
                && SyncValueCodec.isBlankValue(SyncConfigMerger.effective(planned, global, SyncConstants.SET_DATA_SOURCE))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "启用采集前须先选择数据源");
        }
        String start = req.getCollectStartTime() != null ? req.getCollectStartTime()
                : (current == null ? null : current.getCollectStartTime());
        String end = req.getCollectEndTime() != null ? req.getCollectEndTime()
                : (current == null ? null : current.getCollectEndTime());
        if (start != null && end != null && SyncClock.minutesOfClock(start) >= SyncClock.minutesOfClock(end)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采集结束时间须晚于开始时间");
        }

        if (!overrides.isEmpty() || !resetKeys.isEmpty()) {
            store.setStationOverrides(stationId, overrides, resetKeys);
        }

        Map<String, Object> patch = new LinkedHashMap<>(legacyPatch);
        if (enabledVal != null) {
            patch.put("enabled", enabledVal);
        }
        if (statusVal != null) {
            patch.put("status", statusVal);
        }
        if (req.getFrequency() != null) {
            patch.put("frequency", planned.get(SyncConstants.SET_COLLECT_FREQUENCY));
        }
        if (req.getDataSource() != null) {
            patch.put("dataSource", planned.get(SyncConstants.SET_DATA_SOURCE));
        }
        SyncStationConfig config = patch.isEmpty() && current != null ? current : saveRow(stationId, patch);
        return toConfigVO(config);
    }

    // ==================== 内部：采集状态与批次 ====================

    private String collectStateOf(SyncStationConfig config) {
        if (Integer.valueOf(0).equals(config.getStatus())) {
            return "DISABLED";
        }
        if (Integer.valueOf(0).equals(config.getEnabled())) {
            return "UNCONFIGURED";
        }
        return SyncConstants.COLLECT_FAILED.equals(config.getLastCollectStatus()) ? "ABNORMAL" : "NORMAL";
    }

    private SyncLastBatchVO lastBatchOf(Long stationId) {
        List<SyncTask> rows = taskMapper.selectList(new LambdaQueryWrapper<SyncTask>()
                .eq(SyncTask::getStationId, stationId)
                .orderByDesc(SyncTask::getCreateTime).orderByDesc(SyncTask::getId));
        if (rows.isEmpty()) {
            return null;
        }
        SyncTask task = rows.get(0);
        SyncLastBatchVO vo = new SyncLastBatchVO();
        vo.setBatchNo(task.getBatchNo());
        vo.setStatus(task.getStatus());
        vo.setParcelTotal(task.getParcelTotal());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setFailCount(task.getFailCount());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    // ==================== 内部：出参组装 ====================

    private SyncStationConfigVO toConfigVO(SyncStationConfig config) {
        SyncConfigMerger.MergeResult merged = SyncConfigMerger.merge(store.items(), store.globalValues(),
                store.overridesOf(config.getStationId()));
        Map<String, Object> values = merged.values();
        String dataSourceKey = values.get(SyncConstants.SET_DATA_SOURCE) == null ? null
                : String.valueOf(values.get(SyncConstants.SET_DATA_SOURCE));
        String[] template = templateTimeOf(values.get(SyncConstants.SET_TIME_TEMPLATE));

        SyncStationConfigVO vo = new SyncStationConfigVO();
        vo.setId(config.getId());
        vo.setStationId(config.getStationId());
        vo.setStationName(stationName(config.getStationId()));
        vo.setEnabled(Integer.valueOf(1).equals(config.getEnabled()));
        vo.setFrequency(legacyFrequencyOf(values.get(SyncConstants.SET_COLLECT_FREQUENCY)));
        vo.setDataSource(dataSourceKey == null ? null
                : optionLabelOf(SyncConstants.SET_DATA_SOURCE, dataSourceKey));
        vo.setCollectStartTime(config.getCollectStartTime() != null ? config.getCollectStartTime()
                : (template == null ? null : template[0]));
        vo.setCollectEndTime(config.getCollectEndTime() != null ? config.getCollectEndTime()
                : (template == null ? null : template[1]));
        vo.setLastCollectTime(config.getLastCollectTime());
        vo.setLastCollectStatus(config.getLastCollectStatus());
        vo.setStatus(config.getStatus());
        String state = collectStateOf(config);
        vo.setCollectState(state);
        vo.setCollectStateLabel(COLLECT_STATE_LABELS.get(state));
        vo.setLastBatch(lastBatchOf(config.getStationId()));
        vo.setUpdateTime(config.getUpdateTime());
        vo.setValues(values);
        vo.setSources(merged.sources());
        vo.setOverrides(store.overridesOf(config.getStationId()));
        return vo;
    }

    // ==================== 内部：兼容映射 ====================

    /** 频率新码 → 旧码（能反向映射时返回旧码；新增档位原样返回） */
    private String legacyFrequencyOf(Object optionKey) {
        if (optionKey == null) {
            return null;
        }
        SyncConfigOption option = findOptionRaw(SyncConstants.SET_COLLECT_FREQUENCY, String.valueOf(optionKey));
        if (option != null && option.getLegacyCodes() != null && !option.getLegacyCodes().isEmpty()) {
            return option.getLegacyCodes().get(0);
        }
        return String.valueOf(optionKey);
    }

    /** 选项显示名（未命中时回显 Key，避免展示为空） */
    private String optionLabelOf(String setKey, String optionKey) {
        SyncConfigOption option = findOptionRaw(setKey, optionKey);
        return option == null ? optionKey : option.getLabel();
    }

    /** 兼容取值 → 选项 Key：optionKey 与 legacyCodes 二者任一命中即可 */
    private String resolveOptionKey(String setKey, String value, boolean enabledOnly) {
        SyncOptionSet set = store.findOptionSet(setKey);
        if (set == null) {
            return null;
        }
        for (SyncConfigOption option : set.getOptions()) {
            boolean matched = option.getOptionKey().equals(value)
                    || (option.getLegacyCodes() != null && option.getLegacyCodes().contains(value));
            if (matched && (!enabledOnly || Boolean.TRUE.equals(SyncConfigValidator.toBool(option.getEnabled())))) {
                return option.getOptionKey();
            }
        }
        return null;
    }

    /**
     * 历史自由文本 → 数据源选项 Key；未命中受管选项时自动纳管为 source=MIGRATED。
     * <p>
     * 不置空的原因：置空会让「已启用采集」的驿站凭空失去数据源，产生矛盾配置。
     */
    private String resolveDataSourceKey(String text) {
        String hit = resolveOptionKey(SyncConstants.SET_DATA_SOURCE, text.trim(), false);
        if (hit != null) {
            return hit;
        }
        SyncOptionSet set = store.findOptionSet(SyncConstants.SET_DATA_SOURCE);
        int seq = 1;
        if (set != null) {
            for (SyncConfigOption option : set.getOptions()) {
                if (option.getOptionKey() != null && option.getOptionKey().startsWith("MIGRATED_")) {
                    seq++;
                }
            }
        }
        int maxSort = 0;
        if (set != null) {
            for (SyncConfigOption option : set.getOptions()) {
                maxSort = Math.max(maxSort, option.getSort() == null ? 0 : option.getSort());
            }
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

    /** 时段模板 Key → 起止时间 */
    private String[] templateTimeOf(Object optionKey) {
        if (optionKey == null) {
            return null;
        }
        SyncConfigOption option = findOptionRaw(SyncConstants.SET_TIME_TEMPLATE, String.valueOf(optionKey));
        if (option == null || option.getExtraAttrs() == null) {
            return null;
        }
        Object start = option.getExtraAttrs().get("startTime");
        Object end = option.getExtraAttrs().get("endTime");
        return start == null || end == null ? null : new String[]{String.valueOf(start), String.valueOf(end)};
    }

    /** 起止恰好命中某时段模板时返回其 Key */
    private String matchTemplate(String start, String end) {
        SyncOptionSet set = store.findOptionSet(SyncConstants.SET_TIME_TEMPLATE);
        if (set == null) {
            return null;
        }
        for (SyncConfigOption option : set.getOptions()) {
            Map<String, Object> extra = option.getExtraAttrs();
            if (extra != null && start.equals(String.valueOf(extra.get("startTime")))
                    && end.equals(String.valueOf(extra.get("endTime")))) {
                return option.getOptionKey();
            }
        }
        return null;
    }

    private SyncConfigOption findOptionRaw(String setKey, String optionKey) {
        SyncOptionSet set = store.findOptionSet(setKey);
        if (set == null) {
            return null;
        }
        for (SyncConfigOption option : set.getOptions()) {
            if (option.getOptionKey().equals(optionKey)) {
                return option;
            }
        }
        return null;
    }

    // ==================== 内部：配置行读写 ====================

    /** 「按驿站覆盖式保存」：新驿站首存即创建（白名单写入，不接受 id/stationId/最近采集状态） */
    private SyncStationConfig saveRow(Long stationId, Map<String, Object> patch) {
        SyncStationConfig config = findConfig(stationId);
        if (config == null) {
            config = new SyncStationConfig();
            config.setStationId(stationId);
            config.setEnabled(0);
            config.setFrequency(null);
            config.setDataSource(null);
            config.setCollectStartTime("08:00");
            config.setCollectEndTime("20:00");
            config.setLastCollectStatus(SyncConstants.COLLECT_NEVER);
            config.setStatus(1);
            configMapper.insert(config);
        }
        if (patch.containsKey("enabled")) {
            config.setEnabled((Integer) patch.get("enabled"));
        }
        if (patch.containsKey("status")) {
            config.setStatus((Integer) patch.get("status"));
        }
        if (patch.containsKey("frequency")) {
            Object value = patch.get("frequency");
            config.setFrequency(value == null ? null : String.valueOf(value));
        }
        if (patch.containsKey("dataSource")) {
            Object value = patch.get("dataSource");
            config.setDataSource(value == null ? null : String.valueOf(value));
        }
        if (patch.containsKey("collectStartTime")) {
            config.setCollectStartTime(String.valueOf(patch.get("collectStartTime")));
        }
        if (patch.containsKey("collectEndTime")) {
            config.setCollectEndTime(String.valueOf(patch.get("collectEndTime")));
        }
        config.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(config);
        return config;
    }

    private SyncStationConfig findConfig(Long stationId) {
        List<SyncStationConfig> rows = configMapper.selectList(new LambdaQueryWrapper<SyncStationConfig>()
                .eq(SyncStationConfig::getStationId, stationId).orderByAsc(SyncStationConfig::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<SyncStationConfig> scopedConfigs() {
        List<SyncStationConfig> rows = configMapper.selectList(new LambdaQueryWrapper<SyncStationConfig>()
                .orderByAsc(SyncStationConfig::getStationId).orderByAsc(SyncStationConfig::getId));
        LoginUser user = currentUser();
        if (RoleEnum.isAdmin(user.getRole())) {
            return rows;
        }
        Long own = parseStationId(user.getStationId());
        List<SyncStationConfig> scoped = new ArrayList<>();
        for (SyncStationConfig row : rows) {
            if (row.getStationId() != null && row.getStationId().equals(own)) {
                scoped.add(row);
            }
        }
        return scoped;
    }

    // ==================== 内部：小工具 ====================

    /** enabled/status 归一化：接受 0/1 与 true/false；null 表示未传（不改）；其余 → 400 */
    private Integer normalizeFlag(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number) {
            int intValue = number.intValue();
            if (intValue == 0 || intValue == 1) {
                return intValue;
            }
        }
        if (value instanceof String text) {
            if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
                return 0;
            }
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                return 1;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 仅支持 0 / 1");
    }

    private boolean isOwnStation(Long stationId, LoginUser user) {
        Long own = parseStationId(user.getStationId());
        return own != null && own.equals(stationId);
    }

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }

    private LoginUser currentUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private Long parseStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }
}
