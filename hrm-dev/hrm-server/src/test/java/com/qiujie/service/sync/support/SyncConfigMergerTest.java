package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四层合并器 + 计划覆盖计算单测（覆盖：空配置项 / 无覆盖 INHERIT / 有覆盖 OVERRIDE / 重置键 / 生效值）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncConfigMergerTest {

    @Test
    @DisplayName("无覆盖：生效值取全局默认，来源全为 INHERIT")
    void inheritWhenNoOverride() {
        List<SyncConfigItem> items = SyncTestFixtures.items();
        SyncConfigMerger.MergeResult result = SyncConfigMerger.merge(items, SyncTestFixtures.globals(), Map.of());
        assertEquals(5, result.values().size());
        assertEquals("EVERY_240M", result.values().get("collect_frequency"));
        assertNull(result.values().get("data_source"));
        for (String key : result.sources().keySet()) {
            assertEquals(SyncConfigMerger.SOURCE_INHERIT, result.sources().get(key));
        }
    }

    @Test
    @DisplayName("有覆盖：覆盖优先，来源标 OVERRIDE，其余仍 INHERIT")
    void overrideWins() {
        Map<String, Object> overrides = Map.of("data_source", "DUODUOCAI", "retry_times", 5);
        SyncConfigMerger.MergeResult result = SyncConfigMerger.merge(SyncTestFixtures.items(),
                SyncTestFixtures.globals(), overrides);
        assertEquals("DUODUOCAI", result.values().get("data_source"));
        assertEquals(SyncConfigMerger.SOURCE_OVERRIDE, result.sources().get("data_source"));
        assertEquals(5, result.values().get("retry_times"));
        assertEquals(SyncConfigMerger.SOURCE_OVERRIDE, result.sources().get("retry_times"));
        assertEquals("WORKDAY", result.values().get("time_template"));
        assertEquals(SyncConfigMerger.SOURCE_INHERIT, result.sources().get("time_template"));
    }

    @Test
    @DisplayName("空配置项：无键可合并，输出空视图")
    void emptyItems() {
        SyncConfigMerger.MergeResult result = SyncConfigMerger.merge(List.of(), SyncTestFixtures.globals(), Map.of());
        assertTrue(result.values().isEmpty());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    @DisplayName("计划覆盖：先移除 resetKeys 再叠加 overrides")
    void plannedOverridesAppliesResetThenOverrides() {
        Map<String, Object> current = Map.of("data_source", "DUODUOCAI", "retry_times", 5);
        Map<String, Object> overrides = Map.of("collect_frequency", "EVERY_120M");
        Map<String, Object> planned = SyncConfigMerger.plannedOverrides(current, overrides, List.of("data_source"));
        assertFalse(planned.containsKey("data_source"));
        assertEquals(5, planned.get("retry_times"));
        assertEquals("EVERY_120M", planned.get("collect_frequency"));
    }

    @Test
    @DisplayName("生效值：计划覆盖优先，否则回落全局默认")
    void effectiveFallsBackToGlobal() {
        Map<String, Object> planned = Map.of("collect_frequency", "EVERY_120M");
        Map<String, Object> global = SyncTestFixtures.globals();
        assertEquals("EVERY_120M", SyncConfigMerger.effective(planned, global, "collect_frequency"));
        assertEquals("WORKDAY", SyncConfigMerger.effective(planned, global, "time_template"));
        assertNull(SyncConfigMerger.effective(planned, global, "data_source"));
    }
}
