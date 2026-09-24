package com.qiujie.service.sync.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 导入解析与逐行校验单测。
 * <p>
 * 覆盖边界：表头不一致（9508）/ 未知冲突策略（9509）/ 行数超限（5002）/ 列数不正确（缺列与多列）/
 * 非法记录类型（含小写纠正提示）/ 解析阶段不落库（dryRun 同源，纯逻辑不改数据）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncCsvImporterTest {

    private static final int ROW_LIMIT = 1000;

    private String exportAll(SyncConfigCatalog catalog) {
        return SyncCsvCodec.serialize(SyncCsvExporter.buildExport(catalog, "ALL"));
    }

    private List<String> blankRow(int size) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            row.add("");
        }
        return row;
    }

    @Test
    @DisplayName("正常导入：全部已存在 → 无 FAILED、无 CREATE、全部 UPDATE")
    void planAllUpdate() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(exportAll(catalog), "OVERWRITE", catalog, ROW_LIMIT);
        assertTrue(outcome.isOk());
        assertEquals(0, outcome.data().summary().failed());
        assertEquals(0, outcome.data().plan().create());
        assertEquals(0, outcome.data().plan().conflict());
        assertEquals(18, outcome.data().plan().update());
        assertEquals(18, outcome.data().summary().total());
    }

    @Test
    @DisplayName("冲突策略 SKIP：全部已存在 → 全部 SKIP")
    void planAllSkip() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(exportAll(catalog), "SKIP", catalog, ROW_LIMIT);
        assertTrue(outcome.isOk());
        assertEquals(18, outcome.data().plan().skip());
        assertEquals(0, outcome.data().plan().update());
    }

    @Test
    @DisplayName("未知冲突策略 → 9509")
    void unknownConflictStrategy() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(exportAll(catalog), "MERGE", catalog, ROW_LIMIT);
        assertFalse(outcome.isOk());
        assertEquals(9509, outcome.code());
    }

    @Test
    @DisplayName("空内容 / 表头不一致 → 9508")
    void parseErrors() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        assertEquals(9508, SyncCsvImporter.buildImportPlan("", "OVERWRITE", catalog, ROW_LIMIT).code());

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("a", "b", "c"));
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(rows), "OVERWRITE", catalog, ROW_LIMIT);
        assertEquals(9508, outcome.code());
        assertTrue(outcome.message().contains("表头与模板不一致"));
    }

    @Test
    @DisplayName("行数超限 → 5002")
    void rowLimitExceeded() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(exportAll(catalog), "OVERWRITE", catalog, 1);
        assertFalse(outcome.isOk());
        assertEquals(5002, outcome.code());
    }

    @Test
    @DisplayName("缺列（15 列）与多列（17 列）→ 该行 FAILED 且提示列数")
    void columnCountMismatch() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        List<String> itemRow = SyncCsvExporter.buildExport(catalog, "ITEMS").get(1);

        List<String> tooFew = new ArrayList<>(itemRow.subList(0, 15));
        List<List<String>> fewRows = new ArrayList<>();
        fewRows.add(new ArrayList<>(SyncConstants.EXPORT_HEADER));
        fewRows.add(tooFew);
        SyncCsvImporter.PlanOutcome few =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(fewRows), "OVERWRITE", catalog, ROW_LIMIT);
        assertTrue(few.isOk());
        assertEquals(1, few.data().summary().failed());
        assertTrue(few.data().rows().get(0).message().contains("列数不正确"));

        List<String> tooMany = new ArrayList<>(itemRow);
        tooMany.add("extra");
        List<List<String>> manyRows = new ArrayList<>();
        manyRows.add(new ArrayList<>(SyncConstants.EXPORT_HEADER));
        manyRows.add(tooMany);
        SyncCsvImporter.PlanOutcome many =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(manyRows), "OVERWRITE", catalog, ROW_LIMIT);
        assertTrue(many.isOk());
        assertEquals(1, many.data().summary().failed());
        assertTrue(many.data().rows().get(0).message().contains("列数不正确"));
    }

    @Test
    @DisplayName("非法记录类型：小写给出纠正提示，未知类型不给提示")
    void invalidRecordType() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        List<String> lowercase = blankRow(16);
        lowercase.set(0, "item");
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(SyncConstants.EXPORT_HEADER));
        rows.add(lowercase);
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(rows), "OVERWRITE", catalog, ROW_LIMIT);
        assertEquals(1, outcome.data().summary().failed());
        assertTrue(outcome.data().rows().get(0).message().contains("请改为「ITEM」"));

        List<String> unknown = blankRow(16);
        unknown.set(0, "FOO");
        rows.set(1, unknown);
        SyncCsvImporter.PlanOutcome unknownOutcome =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(rows), "OVERWRITE", catalog, ROW_LIMIT);
        assertEquals(1, unknownOutcome.data().summary().failed());
        assertFalse(unknownOutcome.data().rows().get(0).message().contains("请改为"));
    }

    @Test
    @DisplayName("解析阶段不落库：产出动作但不修改快照（dryRun 与落库共用同一解析）")
    void planDoesNotMutateCatalog() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        Map<String, Object> before = Map.copyOf(catalog.overridesOf(1L));
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(exportAll(catalog), "OVERWRITE", catalog, ROW_LIMIT);
        assertNotNull(outcome.data());
        assertFalse(outcome.data().actions().isEmpty());
        assertEquals(before, catalog.overridesOf(1L));
    }

    @Test
    @DisplayName("STATION 引用未声明配置项 / 未知驿站 → 行级 FAILED")
    void stationRowFailures() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        List<String> unknownItem = blankRow(16);
        unknownItem.set(0, "STATION");
        unknownItem.set(1, "not_exists");
        unknownItem.set(13, "城东驿站");
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(SyncConstants.EXPORT_HEADER));
        rows.add(unknownItem);
        SyncCsvImporter.PlanOutcome outcome =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(rows), "OVERWRITE", catalog, ROW_LIMIT);
        assertEquals(1, outcome.data().summary().failed());
        assertTrue(outcome.data().rows().get(0).message().contains("不存在"));

        List<String> unknownStation = blankRow(16);
        unknownStation.set(0, "STATION");
        unknownStation.set(1, "data_source");
        unknownStation.set(13, "不存在的驿站");
        unknownStation.set(14, "DUODUOCAI");
        rows.set(1, unknownStation);
        SyncCsvImporter.PlanOutcome stationOutcome =
                SyncCsvImporter.buildImportPlan(SyncCsvCodec.serialize(rows), "OVERWRITE", catalog, ROW_LIMIT);
        assertEquals(1, stationOutcome.data().summary().failed());
        assertTrue(stationOutcome.data().rows().get(0).message().contains("驿站"));
    }
}
