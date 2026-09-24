package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除影响面计算器单测（覆盖：内置项/选项不可删 9510 / 被覆盖需确认 9503·9505 / 被全局默认硬阻断 9505）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncConfigImpactCalculatorTest {

    @Test
    @DisplayName("内置配置项：canDelete=false，阻断码 9510")
    void builtinItemNotDeletable() {
        SyncConfigItem item = SyncTestFixtures.items().get(0); // builtin=1
        SyncConfigImpactCalculator.ImpactResult result = SyncConfigImpactCalculator.itemImpact(item, List.of());
        assertFalse(result.canDelete());
        assertTrue(result.builtin());
        assertEquals(9510, result.blockers().get(0).code());
    }

    @Test
    @DisplayName("非内置配置项被覆盖引用：canDelete=true 但列 9503 阻断（需 confirm）")
    void referencedItemNeedsConfirm() {
        SyncConfigItem item = SyncTestFixtures.item("custom_key", "自定义项", "TEXT", 0, null, null, null);
        item.setBuiltin(0);
        List<SyncConfigImpactCalculator.StationRef> refs =
                List.of(new SyncConfigImpactCalculator.StationRef(1L, "城东驿站"));
        SyncConfigImpactCalculator.ImpactResult result = SyncConfigImpactCalculator.itemImpact(item, refs);
        assertTrue(result.canDelete());
        assertEquals(1, result.referencedCount());
        assertEquals(9503, result.blockers().get(0).code());
    }

    @Test
    @DisplayName("未引用的非内置配置项：无阻断，可删")
    void unreferencedItemDeletable() {
        SyncConfigItem item = SyncTestFixtures.item("custom_key", "自定义项", "TEXT", 0, null, null, null);
        item.setBuiltin(0);
        SyncConfigImpactCalculator.ImpactResult result = SyncConfigImpactCalculator.itemImpact(item, List.of());
        assertTrue(result.canDelete());
        assertTrue(result.blockers().isEmpty());
        assertEquals(0, result.referencedCount());
    }

    @Test
    @DisplayName("内置选项恒不可删（9510）；被全局默认引用硬阻断（9505，canDelete=false）")
    void optionBuiltinAndGlobal() {
        SyncConfigOption builtin = SyncTestFixtures.option("data_source", "JD", "京东物流", true, true);
        SyncConfigImpactCalculator.ImpactResult builtinResult =
                SyncConfigImpactCalculator.optionImpact(builtin, List.of(), List.of("数据源"), false);
        assertFalse(builtinResult.canDelete());
        assertEquals(9510, builtinResult.blockers().get(0).code());

        SyncConfigOption manual = SyncTestFixtures.option("collect_frequency", "EVERY_30M", "每 30 分钟", false, true);
        SyncConfigImpactCalculator.ImpactResult globalResult =
                SyncConfigImpactCalculator.optionImpact(manual, List.of(), List.of("采集频率"), true);
        assertFalse(globalResult.canDelete());
        assertEquals(9505, globalResult.blockers().get(0).code());
        assertTrue(globalResult.blockers().get(0).message().contains("全局默认值"));
    }

    @Test
    @DisplayName("仅被驿站覆盖引用的非内置选项：可删但需 confirm（9505）")
    void optionReferencedNeedsConfirm() {
        SyncConfigOption manual = SyncTestFixtures.option("data_source", "MIGRATED_1", "丰巢智能柜", false, true);
        List<SyncConfigImpactCalculator.StationRef> refs =
                List.of(new SyncConfigImpactCalculator.StationRef(6L, "城南驿站"));
        SyncConfigImpactCalculator.ImpactResult result =
                SyncConfigImpactCalculator.optionImpact(manual, refs, List.of("数据源"), false);
        assertTrue(result.canDelete());
        assertEquals(9505, result.blockers().get(0).code());
        assertTrue(result.blockers().get(0).message().contains("confirm=true"));
    }
}
