package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置中心四层合并器（纯逻辑）。
 * <p>
 * 取值合并顺序固定为「驿站覆盖 ?? 全局默认」，并同步派生来源标记：
 * <pre>
 *   values[itemKey]  = overrides 含该键 ? 覆盖值 : 全局默认值
 *   sources[itemKey] = overrides 含该键 ? "OVERRIDE" : "INHERIT"
 * </pre>
 * 为什么把「来源」与「值」一起产出：前端需要解释「这个值从哪来」，若在 Controller 侧二次判断，必然与
 * 生效值计算出现两套口径（Mock 已在 {@code getStationConfig} 中把两者一次产出，本类对齐该口径）。
 */
public final class SyncConfigMerger {

    private SyncConfigMerger() {
    }

    /** 合并结果：生效值 + 来源 */
    public record MergeResult(Map<String, Object> values, Map<String, String> sources) {
    }

    /** 生效值来源：驿站覆盖 */
    public static final String SOURCE_OVERRIDE = "OVERRIDE";
    /** 生效值来源：继承全局默认 */
    public static final String SOURCE_INHERIT = "INHERIT";

    /**
     * 逐项合并出「生效值 / 来源」两层视图。
     *
     * @param items        启用态的配置项（决定输出哪些键）
     * @param globalValues 全局默认值（类型化，可含 null 值）
     * @param overrides    驿站覆盖值（类型化；null 视为无覆盖）
     */
    public static MergeResult merge(List<SyncConfigItem> items, Map<String, Object> globalValues,
                                    Map<String, Object> overrides) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, Object> over = overrides == null ? Map.of() : overrides;
        Map<String, Object> global = globalValues == null ? Map.of() : globalValues;
        for (SyncConfigItem item : items) {
            boolean overridden = over.containsKey(item.getItemKey());
            values.put(item.getItemKey(), overridden ? over.get(item.getItemKey()) : global.get(item.getItemKey()));
            sources.put(item.getItemKey(), overridden ? SOURCE_OVERRIDE : SOURCE_INHERIT);
        }
        return new MergeResult(values, sources);
    }

    /**
     * 本次写请求落库后的「计划覆盖集合」= 当前覆盖移除 resetKeys 后再叠加 overrides。
     * 用于写前做跨字段校验（如「启用采集前须先选择数据源」），不落库即可算出生效值。
     */
    public static Map<String, Object> plannedOverrides(Map<String, Object> current, Map<String, Object> overrides,
                                                       List<String> resetKeys) {
        Map<String, Object> planned = new LinkedHashMap<>(current == null ? Map.of() : current);
        if (resetKeys != null) {
            resetKeys.forEach(planned::remove);
        }
        if (overrides != null) {
            planned.putAll(overrides);
        }
        return planned;
    }

    /** 某配置项在计划覆盖下的生效值 */
    public static Object effective(Map<String, Object> plannedOverrides, Map<String, Object> globalValues,
                                   String itemKey) {
        if (plannedOverrides != null && plannedOverrides.containsKey(itemKey)) {
            return plannedOverrides.get(itemKey);
        }
        return globalValues == null ? null : globalValues.get(itemKey);
    }
}
