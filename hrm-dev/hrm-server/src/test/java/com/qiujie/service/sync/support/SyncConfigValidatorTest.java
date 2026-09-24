package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置值校验器单测（覆盖：必填空 9507 / 取值非法 9506 / NUMBER 与 TIME_RANGE 约束边界 /
 * 单选候选缺失与停用 / 约束规范化边界）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncConfigValidatorTest {

    private List<SyncConfigOption> dataSourceOptions() {
        return List.of(
                SyncTestFixtures.option("data_source", "DUODUOCAI", "多多买菜", true, true),
                SyncTestFixtures.option("data_source", "SF", "顺丰速运", true, false));
    }

    @Test
    @DisplayName("必填置空 → 9507；非必填置空 → 通过")
    void requiredEmpty() {
        SyncConfigItem required = SyncTestFixtures.item("data_source", "数据源", "SINGLE_SELECT", 1, null,
                "data_source", null);
        SyncConfigValidator.Error error = SyncConfigValidator.firstError(required, dataSourceOptions(), null);
        assertNotNull(error);
        assertEquals(9507, error.code());

        SyncConfigItem optional = SyncTestFixtures.item("time_template", "采集时段模板", "SINGLE_SELECT", 0, "WORKDAY",
                "time_template", null);
        assertNull(SyncConfigValidator.firstError(optional, List.of(), null));
    }

    @Test
    @DisplayName("NUMBER 边界：超上界 / 低于下界 / 非数字 / 非整数 均 9506，边界内通过")
    void numberBoundary() {
        SyncConfigItem item = SyncTestFixtures.items().get(3);
        assertNull(SyncConfigValidator.validateValue(item, List.of(), 10));
        assertNull(SyncConfigValidator.validateValue(item, List.of(), 0));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), 11).contains("须在 0-10"));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), -1).contains("须在 0-10"));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), "abc").contains("须为数字"));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), 5.5).contains("须为整数"));
        assertEquals(9506, SyncConfigValidator.firstError(item, List.of(), 11).code());
    }

    @Test
    @DisplayName("NUMBER 步长：step>1 且非整数倍 → 报错")
    void numberStep() {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("min", 0);
        constraints.put("max", 100);
        constraints.put("step", 10);
        constraints.put("integerOnly", false);
        SyncConfigItem item = SyncTestFixtures.item("cycle", "周期", "NUMBER", 0, null, null, constraints);
        assertNull(SyncConfigValidator.validateValue(item, List.of(), 20));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), 25).contains("10 的整数倍"));
    }

    @Test
    @DisplayName("TIME_RANGE 边界：结束早于开始 / 24:00 不允许 / 合法区间 / 数组形式")
    void timeRangeBoundary() {
        Map<String, Object> allow = new LinkedHashMap<>();
        allow.put("allowEnd2400", true);
        SyncConfigItem item = SyncTestFixtures.item("window", "时间窗", "TIME_RANGE", 0, null, null, allow);
        assertNull(SyncConfigValidator.validateValue(item, List.of(), "08:00-20:00"));
        assertNull(SyncConfigValidator.validateValue(item, List.of(), "08:00-24:00"));
        assertNull(SyncConfigValidator.validateValue(item, List.of(), List.of("08:00", "20:00")));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), "20:00-08:00").contains("结束时间须晚于开始时间"));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), "8:00-20:00").contains("格式须为 HH:mm"));

        Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("allowEnd2400", false);
        SyncConfigItem strict = SyncTestFixtures.item("window", "时间窗", "TIME_RANGE", 0, null, null, deny);
        assertTrue(SyncConfigValidator.validateValue(strict, List.of(), "08:00-24:00").contains("不允许为 24:00"));
    }

    @Test
    @DisplayName("单选：候选不存在 / 候选已停用 / 集合无候选 三种文案")
    void singleSelect() {
        SyncConfigItem item = SyncTestFixtures.item("data_source", "数据源", "SINGLE_SELECT", 1, null,
                "data_source", null);
        assertNull(SyncConfigValidator.validateValue(item, dataSourceOptions(), "DUODUOCAI"));
        assertTrue(SyncConfigValidator.validateValue(item, dataSourceOptions(), "CAINIAO").contains("选项不存在"));
        assertTrue(SyncConfigValidator.validateValue(item, dataSourceOptions(), "SF").contains("已停用"));
        assertTrue(SyncConfigValidator.validateValue(item, List.of(), "DUODUOCAI").contains("暂无可选项"));
    }

    @Test
    @DisplayName("Key 校验：合法/非法配置项 Key 与选项 Key")
    void keyPatterns() {
        assertNull(SyncConfigValidator.validateItemKey("data_source"));
        assertNotNull(SyncConfigValidator.validateItemKey("Data"));
        assertNotNull(SyncConfigValidator.validateItemKey("a"));
        assertNull(SyncConfigValidator.validateOptionKey("EVERY_2H"));
        assertNotNull(SyncConfigValidator.validateOptionKey("2H"));

        assertNull(SyncConfigValidator.validateOptionKey("EVERY_120M"));
        assertNotNull(SyncConfigValidator.validateOptionKey("有中文"));
    }

    @Test
    @DisplayName("约束规范化：NUMBER 最小>最大 拒绝；TEXT 长度规范化；TIME_RANGE 默认允许 24:00")
    void normalizeConstraints() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("min", 10);
        bad.put("max", 1);
        SyncConfigValidator.Outcome<Map<String, Object>> numberOutcome =
                SyncConfigValidator.normalizeConstraints("NUMBER", bad);
        assertTrue(numberOutcome.isError());

        // TEXT 长度规范化：minLen 缺省归一为 0（契约依据 Mock syncConfigStore.js:996
        // `minLen: c.minLen != null ? Number(c.minLen) : 0`）——与下方 TIME_RANGE 默认值同属「缺省归一」用例
        Map<String, Object> text = new LinkedHashMap<>();
        SyncConfigValidator.Outcome<Map<String, Object>> textOutcome =
                SyncConfigValidator.normalizeConstraints("TEXT", text);
        assertFalse(textOutcome.isError());
        assertEquals(0, ((Number) textOutcome.value().get("minLen")).intValue());

        SyncConfigValidator.Outcome<Map<String, Object>> rangeOutcome =
                SyncConfigValidator.normalizeConstraints("TIME_RANGE", Map.of());
        assertFalse(rangeOutcome.isError());
        assertEquals(Boolean.TRUE, rangeOutcome.value().get("allowEnd2400"));
    }

    @Test
    @DisplayName("附加属性规范化：采集间隔须 >0；时段模板结束须晚于开始")
    void normalizeExtraAttrs() {
        assertTrue(SyncConfigValidator.normalizeExtraAttrs("collect_frequency", Map.of("intervalMinutes", 0)).isError());
        assertFalse(SyncConfigValidator.normalizeExtraAttrs("collect_frequency", Map.of("intervalMinutes", 60)).isError());
        assertTrue(SyncConfigValidator.normalizeExtraAttrs("time_template",
                Map.of("startTime", "20:00", "endTime", "08:00")).isError());
        SyncConfigValidator.Outcome<Map<String, Object>> ok =
                SyncConfigValidator.normalizeExtraAttrs("time_template", Map.of("startTime", "08:00", "endTime", "24:00"));
        assertFalse(ok.isError());
        assertEquals("24:00", ok.value().get("endTime"));
    }
}
