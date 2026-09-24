package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除影响面计算器（纯逻辑）。
 * <p>
 * 语义（设计 B.6 / C.4）：
 * <ul>
 *   <li>内置项 / 内置选项恒不可删（9510），只能停用；</li>
 *   <li>配置项被驿站覆盖引用时需 confirm（9503）；</li>
 *   <li>选项被「全局默认」引用是<b>硬阻断</b>（9505，删掉会让未覆盖驿站落到未配置）；仅被驿站覆盖引用时需 confirm（9505）。</li>
 * </ul>
 * 为什么独立成纯类：影响面既用于「删除前查询」，也用于「删除时是否放行」的判定依据，两处必须同一口径。
 */
public final class SyncConfigImpactCalculator {

    private SyncConfigImpactCalculator() {
    }

    /** 引用某配置项 / 选项的驿站（含名称，供确认框展示） */
    public record StationRef(Long stationId, String stationName) {
    }

    /** 阻断原因（错误码 + 文案），空列表 = 当前可删 */
    public record Blocker(int code, String message) {
    }

    /** 影响面结果（配置项与选项同构；optionKey 仅选项有值） */
    public record ImpactResult(String itemKey, String optionKey, boolean canDelete, boolean builtin,
                               List<StationRef> referencedStations, int referencedCount, List<Blocker> blockers) {
    }

    private static final int CODE_ITEM_IN_USE = 9503;
    private static final int CODE_OPTION_IN_USE = 9505;
    private static final int CODE_BUILTIN_NOT_DELETABLE = 9510;

    /** 配置项影响面 */
    public static ImpactResult itemImpact(SyncConfigItem item, List<StationRef> referencedStations) {
        List<StationRef> refs = referencedStations == null ? List.of() : referencedStations;
        boolean builtin = Boolean.TRUE.equals(SyncConfigValidator.toBool(item.getBuiltin()));
        List<Blocker> blockers = new ArrayList<>();
        if (builtin) {
            blockers.add(new Blocker(CODE_BUILTIN_NOT_DELETABLE,
                    "「" + item.getName() + "」为系统内置配置项，只能停用"));
        } else if (!refs.isEmpty()) {
            blockers.add(new Blocker(CODE_ITEM_IN_USE,
                    "该配置项正被 " + refs.size() + " 个驿站覆盖，确认删除请传 confirm=true"));
        }
        return new ImpactResult(item.getItemKey(), null, !builtin, builtin, refs, refs.size(), blockers);
    }

    /**
     * 选项影响面。
     *
     * @param option         目标选项
     * @param referencedStations 覆盖值指向该选项的驿站
     * @param ownerNames     引用该选项集的配置项名称（用于全局默认阻断文案）
     * @param inGlobal       该选项是否为某配置项的全局默认值
     */
    public static ImpactResult optionImpact(SyncConfigOption option, List<StationRef> referencedStations,
                                            List<String> ownerNames, boolean inGlobal) {
        List<StationRef> refs = referencedStations == null ? List.of() : referencedStations;
        boolean builtin = Boolean.TRUE.equals(SyncConfigValidator.toBool(option.getBuiltin()));
        List<Blocker> blockers = new ArrayList<>();
        if (builtin) {
            blockers.add(new Blocker(CODE_BUILTIN_NOT_DELETABLE,
                    "「" + option.getLabel() + "」为系统内置选项，只能停用"));
        } else if (inGlobal) {
            blockers.add(new Blocker(CODE_OPTION_IN_USE,
                    "「" + option.getLabel() + "」是全局默认值，删除后将导致「"
                            + String.join("、", ownerNames == null ? List.of() : ownerNames)
                            + "」缺少全局默认值，建议改为停用"));
        } else if (!refs.isEmpty()) {
            blockers.add(new Blocker(CODE_OPTION_IN_USE,
                    "「" + option.getLabel() + "」正被 " + refs.size() + " 个驿站覆盖，确认删除请传 confirm=true"));
        }
        return new ImpactResult(null, option.getOptionKey(), !builtin && !inGlobal, builtin, refs, refs.size(),
                blockers);
    }
}
