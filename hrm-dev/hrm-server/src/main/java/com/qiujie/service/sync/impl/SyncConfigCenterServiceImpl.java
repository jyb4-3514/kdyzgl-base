package com.qiujie.service.sync.impl;

import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.sync.SyncConfigGlobalRequest;
import com.qiujie.dto.sync.SyncConfigImportRequest;
import com.qiujie.dto.sync.SyncConfigItemRequest;
import com.qiujie.dto.sync.SyncConfigOptionRequest;
import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.SyncConfigItemMapper;
import com.qiujie.mapper.SyncConfigOptionMapper;
import com.qiujie.service.sync.SyncConfigCenterService;
import com.qiujie.service.sync.SyncExportFile;
import com.qiujie.service.sync.support.SyncConfigCatalogSnapshot;
import com.qiujie.service.sync.support.SyncConfigImpactCalculator;
import com.qiujie.service.sync.support.SyncConfigStore;
import com.qiujie.service.sync.support.SyncConfigValidator;
import com.qiujie.service.sync.support.SyncConstants;
import com.qiujie.service.sync.support.SyncCsvCodec;
import com.qiujie.service.sync.support.SyncCsvExporter;
import com.qiujie.service.sync.support.SyncCsvImporter;
import com.qiujie.service.sync.support.SyncOptionSet;
import com.qiujie.service.sync.support.SyncValueCodec;
import com.qiujie.vo.sync.SyncConfigItemListVO;
import com.qiujie.vo.sync.SyncConfigItemVO;
import com.qiujie.vo.sync.SyncConfigOptionSetVO;
import com.qiujie.vo.sync.SyncConfigOptionVO;
import com.qiujie.vo.sync.SyncGlobalConfigVO;
import com.qiujie.vo.sync.SyncImpactBlockerVO;
import com.qiujie.vo.sync.SyncImpactStationVO;
import com.qiujie.vo.sync.SyncImpactVO;
import com.qiujie.vo.sync.SyncImportAppliedVO;
import com.qiujie.vo.sync.SyncImportPlanVO;
import com.qiujie.vo.sync.SyncImportResultVO;
import com.qiujie.vo.sync.SyncImportRowVO;
import com.qiujie.vo.sync.SyncImportSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置中心服务实现（M9，13 接口，Mock {@code routes/syncConfigCenter.js} + {@code syncConfigStore.js}）。
 * <p>
 * 四层模型：配置项定义（{@code sync_config_item}）+ 选项集（{@code sync_config_option}）= 元数据层；
 * 全局默认（{@code sync_config_global}）与驿站覆盖（{@code sync_config_station_override}）= 取值层。
 * 取值合并与来源派生由 {@link SyncConfigStore} / {@link com.qiujie.service.sync.support.SyncConfigMerger} 承担。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncConfigCenterServiceImpl implements SyncConfigCenterService {

    private final SyncConfigItemMapper itemMapper;
    private final SyncConfigOptionMapper optionMapper;
    private final SyncConfigStore store;
    private final AlgoProperties algoProperties;

    // ==================== 配置项 / 选项集 ====================

    @Override
    @Transactional(readOnly = true)
    public SyncConfigItemListVO listItems() {
        SyncConfigItemListVO vo = new SyncConfigItemListVO();
        for (SyncConfigItem item : store.items()) {
            vo.getItems().add(toItemVO(item));
        }
        for (SyncOptionSet set : store.optionSets()) {
            vo.getOptionSets().add(toOptionSetVO(set));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigItemVO createItem(SyncConfigItemRequest request) {
        String itemKey = trim(request == null ? null : request.getItemKey());
        String keyError = SyncConfigValidator.validateItemKey(itemKey);
        if (keyError != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, keyError);
        }
        if (store.findItem(itemKey) != null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_KEY_EXISTS);
        }
        SyncConfigItem built = normalizeItem(itemKey, safe(request), null);
        itemMapper.insert(built);
        return toItemVO(built);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigItemVO updateItem(String itemKey, SyncConfigItemRequest request) {
        SyncConfigItem base = store.findItem(itemKey);
        if (base == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS);
        }
        SyncConfigItem normalized = normalizeItem(base.getItemKey(), safe(request), base);
        itemMapper.updateById(normalized);
        return toItemVO(normalized);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(String itemKey, boolean confirm) {
        SyncConfigItem item = requireItem(itemKey);
        if (Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getBuiltin()))) {
            throw new BusinessException(ErrorCode.SYNC_BUILTIN_NOT_DELETABLE,
                    "「" + item.getName() + "」为系统内置配置项，只能停用");
        }
        List<Long> referenced = store.stationsReferencingItem(itemKey);
        if (!referenced.isEmpty() && !confirm) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_IN_USE,
                    "该配置项正被 " + referenced.size() + " 个驿站覆盖，确认删除请传 confirm=true");
        }
        itemMapper.deleteById(item.getId());
        // 同步清理全局默认与受影响驿站的覆盖值，避免悬空引用
        store.deleteGlobal(itemKey);
        store.stripOverridesByItemKey(itemKey);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncImpactVO itemImpact(String itemKey) {
        SyncConfigItem item = requireItem(itemKey);
        List<SyncConfigImpactCalculator.StationRef> refs = stationRefs(store.stationsReferencingItem(itemKey));
        return toImpactVO(SyncConfigImpactCalculator.itemImpact(item, refs));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigOptionVO createOption(String itemKey, SyncConfigOptionRequest request) {
        String setKey = resolveOptionSetKey(itemKey).setKey;
        return createOptionBySet(setKey, safe(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigOptionVO updateOption(String itemKey, String optionKey, SyncConfigOptionRequest request) {
        String setKey = resolveOptionSetKey(itemKey).setKey;
        return updateOptionBySet(setKey, optionKey, safe(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOption(String itemKey, String optionKey, boolean confirm) {
        String setKey = resolveOptionSetKey(itemKey).setKey;
        deleteOptionBySet(setKey, optionKey, confirm);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncImpactVO optionImpact(String itemKey, String optionKey) {
        String setKey = resolveOptionSetKey(itemKey).setKey;
        return optionImpactBySet(setKey, optionKey);
    }

    // ==================== 全局默认 ====================

    @Override
    @Transactional(readOnly = true)
    public SyncGlobalConfigVO globalValues() {
        SyncGlobalConfigVO vo = new SyncGlobalConfigVO();
        vo.setValues(store.globalValues());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncGlobalConfigVO saveGlobalValues(SyncConfigGlobalRequest request) {
        store.saveGlobalValues(request == null ? null : request.getValues());
        return globalValues();
    }

    // ==================== CSV 导出 / 导入 ====================

    @Override
    @Transactional(readOnly = true)
    public SyncExportFile export(String scope) {
        String effective = (scope == null || scope.isEmpty()) ? "ITEMS_GLOBAL" : scope;
        if (SyncCsvExporter.exportScopeInvalid(effective)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "scope 仅支持 ITEMS / ITEMS_GLOBAL / ALL / TEMPLATE");
        }
        SyncConfigCatalogSnapshot catalog = store.catalog();
        // 列数与契约常量漂移自检：配置项只是规模参数，真实列序以 SyncConstants.EXPORT_HEADER 为准
        if (SyncConstants.EXPORT_HEADER.size() != algoProperties.getSync().getCsvColumnCount()) {
            log.warn("hrm.algo.sync.csvColumnCount={} 与契约表头列数 {} 不一致，以契约表头为准",
                    algoProperties.getSync().getCsvColumnCount(), SyncConstants.EXPORT_HEADER.size());
        }
        List<List<String>> rows = "TEMPLATE".equals(effective)
                ? SyncCsvExporter.buildTemplate(catalog)
                : SyncCsvExporter.buildExport(catalog, effective);
        byte[] bytes = SyncCsvCodec.toBytes(rows);
        String filename = SyncCsvExporter.exportFilename(effective, LocalDate.now());
        log.debug("同步配置导出：scope={}, 内存流 {} 字节，文件名={}", effective, bytes.length, filename);
        return new SyncExportFile(bytes, filename);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncImportResultVO importConfig(SyncConfigImportRequest request) {
        SyncConfigImportRequest req = request == null ? new SyncConfigImportRequest() : request;
        if (!(req.getContent() instanceof String)) {
            throw new BusinessException(ErrorCode.SYNC_IMPORT_PARSE_ERROR, "导入内容须为 CSV 文本");
        }
        String onConflict = req.getOnConflict() == null ? "OVERWRITE" : req.getOnConflict().toUpperCase();
        SyncConfigCatalogSnapshot catalog = store.catalog();
        SyncCsvImporter.PlanOutcome outcome = SyncCsvImporter.buildImportPlan(req.getContent(), onConflict, catalog,
                algoProperties.getSync().getImportRowLimit());
        if (!outcome.isOk()) {
            ErrorCode errorCode = toErrorCode(outcome.code());
            throw new BusinessException(errorCode,
                    outcome.message() == null ? errorCode.getMessage() : outcome.message());
        }
        SyncCsvImporter.ImportPlanResult plan = outcome.data();
        SyncImportResultVO vo = new SyncImportResultVO();
        vo.setDryRun(Boolean.TRUE.equals(req.getDryRun()));
        vo.setSummary(new SyncImportSummaryVO(plan.summary().total(), plan.summary().ok(), plan.summary().failed(),
                plan.summary().warning()));
        for (SyncCsvImporter.ImportRow row : plan.rows()) {
            vo.getRows().add(new SyncImportRowVO(row.rowNo(), row.type(), row.itemKey(), row.optionKey(),
                    row.stationName(), row.level(), row.message()));
        }
        vo.setPlan(new SyncImportPlanVO(plan.plan().create(), plan.plan().update(), plan.plan().skip(),
                plan.plan().conflict()));
        if (!Boolean.TRUE.equals(req.getDryRun())) {
            vo.setApplied(applyImport(plan.actions()));
        }
        return vo;
    }

    // ==================== 内部：选项集解析 ====================

    private record ItemSet(SyncConfigItem item, String setKey) {
    }

    /** 选项集路由以配置项 Key 定位：先把 itemKey 解析成选项集，非单选型直接拦下 */
    private ItemSet resolveOptionSetKey(String itemKey) {
        SyncConfigItem item = store.findItem(itemKey);
        if (item == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS);
        }
        if (!"SINGLE_SELECT".equals(item.getValueType()) || item.getOptionSetKey() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "「" + item.getName() + "」不是单选项配置，没有候选项");
        }
        return new ItemSet(item, item.getOptionSetKey());
    }

    private SyncConfigOptionVO createOptionBySet(String setKey, SyncConfigOptionRequest request) {
        SyncOptionSet set = store.findOptionSet(setKey);
        if (set == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS, "选项集「" + setKey + "」不存在");
        }
        String optionKey = trim(request.getOptionKey());
        String keyError = SyncConfigValidator.validateOptionKey(optionKey);
        if (keyError != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, keyError);
        }
        if (findOption(set, optionKey) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "选项 Key「" + optionKey + "」在选项集「" + set.getName() + "」中已存在");
        }
        SyncConfigOption built = normalizeOption(set, optionKey, request, null);
        optionMapper.insert(built);
        return toOptionVO(built);
    }

    private SyncConfigOptionVO updateOptionBySet(String setKey, String optionKey, SyncConfigOptionRequest request) {
        SyncOptionSet set = store.findOptionSet(setKey);
        if (set == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS, "选项集「" + setKey + "」不存在");
        }
        SyncConfigOption base = findOption(set, optionKey);
        if (base == null) {
            throw new BusinessException(ErrorCode.SYNC_OPTION_NOT_EXISTS);
        }
        SyncConfigOption normalized = normalizeOption(set, base.getOptionKey(), request, base);
        optionMapper.updateById(normalized);
        return toOptionVO(normalized);
    }

    private void deleteOptionBySet(String setKey, String optionKey, boolean confirm) {
        SyncOptionSet set = store.findOptionSet(setKey);
        if (set == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS, "选项集「" + setKey + "」不存在");
        }
        SyncConfigOption option = findOption(set, optionKey);
        if (option == null) {
            throw new BusinessException(ErrorCode.SYNC_OPTION_NOT_EXISTS);
        }
        if (Boolean.TRUE.equals(SyncConfigValidator.toBool(option.getBuiltin()))) {
            throw new BusinessException(ErrorCode.SYNC_BUILTIN_NOT_DELETABLE,
                    "「" + option.getLabel() + "」为系统内置选项，只能停用");
        }
        if (store.optionInGlobal(setKey, optionKey)) {
            throw new BusinessException(ErrorCode.SYNC_OPTION_IN_USE,
                    "「" + option.getLabel() + "」是全局默认值，删除后将导致「"
                            + String.join("、", store.ownerNames(setKey)) + "」缺少全局默认值，建议改为停用");
        }
        List<Long> referenced = store.stationsReferencingOption(setKey, optionKey);
        if (!referenced.isEmpty() && !confirm) {
            throw new BusinessException(ErrorCode.SYNC_OPTION_IN_USE,
                    "「" + option.getLabel() + "」正被 " + referenced.size() + " 个驿站覆盖，确认删除请传 confirm=true");
        }
        optionMapper.deleteById(option.getId());
        store.stripOverridesByOption(setKey, optionKey);
    }

    private SyncImpactVO optionImpactBySet(String setKey, String optionKey) {
        SyncOptionSet set = store.findOptionSet(setKey);
        if (set == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS, "选项集「" + setKey + "」不存在");
        }
        SyncConfigOption option = findOption(set, optionKey);
        if (option == null) {
            throw new BusinessException(ErrorCode.SYNC_OPTION_NOT_EXISTS);
        }
        List<SyncConfigImpactCalculator.StationRef> refs = stationRefs(store.stationsReferencingOption(setKey, optionKey));
        boolean inGlobal = store.optionInGlobal(setKey, optionKey);
        return toImpactVO(SyncConfigImpactCalculator.optionImpact(option, refs, store.ownerNames(setKey), inGlobal));
    }

    // ==================== 内部：规范化 ====================

    /** 配置项规范化：逐字段校验并产出可持久化实体（对齐 Mock {@code normalizeItem}） */
    private SyncConfigItem normalizeItem(String itemKey, SyncConfigItemRequest req, SyncConfigItem base) {
        SyncConfigItem item = base == null ? new SyncConfigItem() : base;
        item.setItemKey(itemKey);

        String name = req.getName() != null ? req.getName().trim() : (base != null ? base.getName() : "");
        if (name == null || name.isEmpty() || name.length() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配置项显示名须为 1-20 字符");
        }
        item.setName(name);

        String valueType = base != null ? base.getValueType() : req.getValueType();
        if (valueType == null || !SyncConstants.VALUE_TYPES.contains(valueType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "值类型仅支持 " + String.join(" / ", SyncConstants.VALUE_TYPES));
        }
        item.setValueType(valueType);

        String scope = req.getScope() != null ? req.getScope() : (base != null ? base.getScope() : "STATION");
        if (scope == null || !SyncConstants.ITEM_SCOPES.contains(scope)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "生效范围仅支持 GLOBAL / STATION");
        }
        item.setScope(scope);

        Integer sort = req.getSort() != null ? req.getSort()
                : (base != null ? base.getSort() : store.items().size() * 10 + 10);
        if (sort == null || sort < 0 || sort > 9999) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排序须为 0-9999 的整数");
        }
        item.setSort(sort);

        boolean required = req.getRequired() != null ? req.getRequired()
                : (base != null && Boolean.TRUE.equals(SyncConfigValidator.toBool(base.getRequired())));
        item.setRequired(required ? 1 : 0);
        boolean enabled = req.getEnabled() != null ? req.getEnabled()
                : (base == null || Boolean.TRUE.equals(SyncConfigValidator.toBool(base.getEnabled())));
        item.setEnabled(enabled ? 1 : 0);

        String unit = req.getUnit() != null ? req.getUnit() : (base != null ? base.getUnit() : "");
        if (unit == null) {
            unit = "";
        }
        if (unit.length() > 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单位长度不超过 8 字符");
        }
        item.setUnit(unit);

        String description = req.getDescription() != null ? req.getDescription()
                : (base != null ? base.getDescription() : "");
        if (description == null) {
            description = "";
        }
        if (description.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "说明长度不超过 100 字符");
        }
        item.setDescription(description);

        if (req.getConstraints() != null) {
            SyncConfigValidator.Outcome<Map<String, Object>> outcome =
                    SyncConfigValidator.normalizeConstraints(valueType, req.getConstraints());
            if (outcome.isError()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, outcome.error());
            }
            item.setConstraints(outcome.value());
        } else if (base == null) {
            item.setConstraints(null);
        }

        String optionSetKey = req.getOptionSetKey() != null ? req.getOptionSetKey()
                : (base != null ? base.getOptionSetKey() : null);
        if ("SINGLE_SELECT".equals(valueType)) {
            if (optionSetKey == null || optionSetKey.isEmpty()) {
                optionSetKey = itemKey;
            }
            if (store.findOptionSet(optionSetKey) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关联的选项集「" + optionSetKey + "」不存在");
            }
        } else {
            optionSetKey = null;
        }
        item.setOptionSetKey(optionSetKey);
        item.setBuiltin(base != null ? base.getBuiltin() : 0);

        Object defaultValue = req.getDefaultValue() != null ? req.getDefaultValue()
                : (base != null ? SyncValueCodec.toTyped(base.getValueType(), base.getDefaultValue()) : null);
        if (!SyncValueCodec.isBlankValue(defaultValue)) {
            String message = SyncConfigValidator.validateValue(item, store.optionsOfSet(optionSetKey), defaultValue);
            if (message != null) {
                throw new BusinessException(ErrorCode.SYNC_VALUE_INVALID, message);
            }
        }
        item.setDefaultValue(SyncValueCodec.toStored(valueType, defaultValue));
        return item;
    }

    /** 选项规范化（对齐 Mock {@code normalizeOption}） */
    private SyncConfigOption normalizeOption(SyncOptionSet set, String optionKey, SyncConfigOptionRequest req,
                                             SyncConfigOption base) {
        SyncConfigOption option = base == null ? new SyncConfigOption() : base;
        option.setSetKey(set.getSetKey());
        option.setOptionKey(optionKey);

        String label = req.getLabel() != null ? req.getLabel().trim() : (base != null ? base.getLabel() : "");
        if (label == null || label.isEmpty() || label.length() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选项显示名须为 1-20 字符");
        }
        option.setLabel(label);

        String remark = req.getRemark() != null ? req.getRemark() : (base != null ? base.getRemark() : "");
        if (remark == null) {
            remark = "";
        }
        if (remark.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "备注长度不超过 100 字符");
        }
        option.setRemark(remark.isEmpty() ? null : remark);

        Integer sort = req.getSort() != null ? req.getSort()
                : (base != null ? base.getSort() : nextOptionSort(set));
        if (sort == null || sort < 0 || sort > 9999) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排序须为 0-9999 的整数");
        }
        option.setSort(sort);

        boolean enabled = req.getEnabled() != null ? req.getEnabled()
                : (base == null || Boolean.TRUE.equals(SyncConfigValidator.toBool(base.getEnabled())));
        option.setEnabled(enabled ? 1 : 0);

        if (req.getExtraAttrs() != null) {
            SyncConfigValidator.Outcome<Map<String, Object>> outcome =
                    SyncConfigValidator.normalizeExtraAttrs(set.getSetKey(), req.getExtraAttrs());
            if (outcome.isError()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, outcome.error());
            }
            option.setExtraAttrs(outcome.value());
        } else if (base == null) {
            option.setExtraAttrs(null);
        }

        option.setBuiltin(base != null ? base.getBuiltin() : 0);
        option.setSource(base != null ? base.getSource() : "MANUAL");
        option.setLegacyCodes(base != null ? base.getLegacyCodes() : List.of());
        return option;
    }

    private int nextOptionSort(SyncOptionSet set) {
        int max = 0;
        for (SyncConfigOption option : set.getOptions()) {
            int value = option.getSort() == null ? 0 : option.getSort();
            max = Math.max(max, value);
        }
        return max + 10;
    }

    // ==================== 内部：CSV 落库 ====================

    /** 按计划顺序应用动作；单行动作失败只跳过该行（不因一行异常回滚整批，与 Mock applyImport 同源） */
    private SyncImportAppliedVO applyImport(List<SyncCsvImporter.Action> actions) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (SyncCsvImporter.Action action : actions) {
            if ("SKIP".equals(action.getKind())) {
                skipped++;
                continue;
            }
            boolean update = "UPDATE".equals(action.getKind());
            try {
                switch (action.getType()) {
                    case "ITEM" -> {
                        SyncConfigItemRequest req = itemRequestFromPayload(action.getPayload());
                        if (update) {
                            updateItem(action.getItemKey(), req);
                            updated++;
                        } else {
                            req.setItemKey(action.getItemKey());
                            createItem(req);
                            created++;
                        }
                    }
                    case "OPTION" -> {
                        SyncConfigOptionRequest req = optionRequestFromPayload(action.getPayload());
                        if (update) {
                            updateOptionBySet(action.getSetKey(), action.getOptionKey(), req);
                            updated++;
                        } else {
                            req.setOptionKey(action.getOptionKey());
                            createOptionBySet(action.getSetKey(), req);
                            created++;
                        }
                    }
                    case "GLOBAL" -> {
                        Map<String, Object> values = new LinkedHashMap<>();
                        values.put(action.getItemKey(), action.getValue());
                        store.saveGlobalValues(values);
                        if (update) {
                            updated++;
                        } else {
                            created++;
                        }
                    }
                    case "STATION" -> {
                        store.setStationOverride(action.getStationId(), action.getItemKey(), action.getValue());
                        if (update) {
                            updated++;
                        } else {
                            created++;
                        }
                    }
                    default -> log.warn("未知导入动作类型：{}", action.getType());
                }
            } catch (BusinessException e) {
                log.warn("CSV 导入行落库失败已跳过：type={}, kind={}, itemKey={}, optionKey={}, code={}, message={}",
                        action.getType(), action.getKind(), action.getItemKey(), action.getOptionKey(), e.getCode(),
                        e.getMessage());
            }
        }
        return new SyncImportAppliedVO(created, updated, skipped);
    }

    private SyncConfigItemRequest itemRequestFromPayload(Map<String, Object> payload) {
        SyncConfigItemRequest req = new SyncConfigItemRequest();
        if (payload == null) {
            return req;
        }
        req.setName(asString(payload.get("name")));
        req.setRequired(asBoolean(payload.get("required")));
        req.setDefaultValue(payload.get("defaultValue"));
        req.setUnit(asString(payload.get("unit")));
        req.setEnabled(asBoolean(payload.get("enabled")));
        req.setDescription(asString(payload.get("description")));
        if (payload.get("constraints") instanceof Map<?, ?> map) {
            req.setConstraints(toStringMap(map));
        }
        if (payload.get("sort") instanceof Number number) {
            req.setSort(number.intValue());
        }
        req.setItemKey(asString(payload.get("itemKey")));
        req.setValueType(asString(payload.get("valueType")));
        req.setOptionSetKey(asString(payload.get("optionSetKey")));
        req.setScope(asString(payload.get("scope")));
        return req;
    }

    private SyncConfigOptionRequest optionRequestFromPayload(Map<String, Object> payload) {
        SyncConfigOptionRequest req = new SyncConfigOptionRequest();
        if (payload == null) {
            return req;
        }
        req.setLabel(asString(payload.get("label")));
        req.setEnabled(asBoolean(payload.get("enabled")));
        req.setRemark(asString(payload.get("remark")));
        if (payload.get("extraAttrs") instanceof Map<?, ?> map) {
            req.setExtraAttrs(toStringMap(map));
        }
        return req;
    }

    // ==================== 内部：出参组装 ====================

    private SyncConfigItemVO toItemVO(SyncConfigItem item) {
        SyncConfigItemVO vo = new SyncConfigItemVO();
        vo.setItemKey(item.getItemKey());
        vo.setName(item.getName());
        vo.setDescription(item.getDescription());
        vo.setValueType(item.getValueType());
        vo.setRequired(Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getRequired())));
        vo.setDefaultValue(SyncValueCodec.toTyped(item.getValueType(), item.getDefaultValue()));
        vo.setUnit(item.getUnit());
        vo.setConstraints(item.getConstraints());
        vo.setOptionSetKey(item.getOptionSetKey());
        vo.setScope(item.getScope());
        vo.setSort(item.getSort());
        vo.setEnabled(Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getEnabled())));
        vo.setBuiltin(Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getBuiltin())));
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }

    private SyncConfigOptionVO toOptionVO(SyncConfigOption option) {
        SyncConfigOptionVO vo = new SyncConfigOptionVO();
        vo.setOptionKey(option.getOptionKey());
        vo.setLabel(option.getLabel());
        vo.setExtraAttrs(option.getExtraAttrs());
        vo.setSort(option.getSort());
        vo.setEnabled(Boolean.TRUE.equals(SyncConfigValidator.toBool(option.getEnabled())));
        vo.setBuiltin(Boolean.TRUE.equals(SyncConfigValidator.toBool(option.getBuiltin())));
        vo.setSource(option.getSource());
        vo.setLegacyCodes(option.getLegacyCodes());
        vo.setRemark(option.getRemark());
        return vo;
    }

    private SyncConfigOptionSetVO toOptionSetVO(SyncOptionSet set) {
        SyncConfigOptionSetVO vo = new SyncConfigOptionSetVO();
        vo.setSetKey(set.getSetKey());
        vo.setName(set.getName());
        vo.setDescription(set.getDescription());
        vo.setBuiltin(set.isBuiltin());
        vo.setEnabled(set.isEnabled());
        for (SyncConfigOption option : set.getOptions()) {
            vo.getOptions().add(toOptionVO(option));
        }
        return vo;
    }

    private SyncImpactVO toImpactVO(SyncConfigImpactCalculator.ImpactResult result) {
        SyncImpactVO vo = new SyncImpactVO();
        vo.setItemKey(result.itemKey());
        vo.setOptionKey(result.optionKey());
        vo.setCanDelete(result.canDelete());
        vo.setBuiltin(result.builtin());
        vo.setReferencedCount(result.referencedCount());
        for (SyncConfigImpactCalculator.StationRef ref : result.referencedStations()) {
            vo.getReferencedStations().add(new SyncImpactStationVO(ref.stationId(), ref.stationName()));
        }
        for (SyncConfigImpactCalculator.Blocker blocker : result.blockers()) {
            vo.getBlockers().add(new SyncImpactBlockerVO(blocker.code(), blocker.message()));
        }
        return vo;
    }

    private List<SyncConfigImpactCalculator.StationRef> stationRefs(List<Long> stationIds) {
        Map<Long, String> names = store.stationNames();
        List<SyncConfigImpactCalculator.StationRef> refs = new ArrayList<>(stationIds.size());
        for (Long stationId : stationIds) {
            refs.add(new SyncConfigImpactCalculator.StationRef(stationId, names.get(stationId)));
        }
        return refs;
    }

    // ==================== 内部：小工具 ====================

    private SyncConfigItem requireItem(String itemKey) {
        SyncConfigItem item = store.findItem(itemKey);
        if (item == null) {
            throw new BusinessException(ErrorCode.SYNC_ITEM_NOT_EXISTS);
        }
        return item;
    }

    private SyncConfigOption findOption(SyncOptionSet set, String optionKey) {
        if (optionKey == null) {
            return null;
        }
        for (SyncConfigOption option : set.getOptions()) {
            if (optionKey.equals(option.getOptionKey())) {
                return option;
            }
        }
        return null;
    }

    private SyncConfigItemRequest safe(SyncConfigItemRequest request) {
        return request == null ? new SyncConfigItemRequest() : request;
    }

    private SyncConfigOptionRequest safe(SyncConfigOptionRequest request) {
        return request == null ? new SyncConfigOptionRequest() : request;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /** CSV 解析错误码 → 枚举（枚举自带默认文案；message 为 null 时用枚举文案） */
    private ErrorCode toErrorCode(int code) {
        return switch (code) {
            case 5002 -> ErrorCode.IMPORT_TOO_MANY_ROWS;
            case 9509 -> ErrorCode.SYNC_IMPORT_CONFLICT_INVALID;
            default -> ErrorCode.SYNC_IMPORT_PARSE_ERROR;
        };
    }
}
