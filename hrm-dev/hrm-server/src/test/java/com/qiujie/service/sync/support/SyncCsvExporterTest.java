package com.qiujie.service.sync.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 行构建单测（覆盖：16 列固定表头 / 四种记录类型行 / 导出范围差异 / 模板 / 文件名 / 范围校验）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncCsvExporterTest {

    @Test
    @DisplayName("表头逐列对齐 16 列固定契约")
    void headerAligned() {
        List<List<String>> rows = SyncCsvExporter.buildExport(SyncTestFixtures.catalog(), "ITEMS");
        assertEquals(SyncConstants.EXPORT_HEADER, rows.get(0));
        assertEquals(16, rows.get(0).size());
    }

    @Test
    @DisplayName("导出范围：ITEMS 仅定义 / ITEMS_GLOBAL 含全局默认 / ALL 含驿站覆盖")
    void scopeVariants() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        assertEquals(12, SyncCsvExporter.buildExport(catalog, "ITEMS").size());          // 1+5+6
        assertEquals(16, SyncCsvExporter.buildExport(catalog, "ITEMS_GLOBAL").size());   // +4 全局非空
        assertEquals(19, SyncCsvExporter.buildExport(catalog, "ALL").size());            // +3 驿站覆盖
    }

    @Test
    @DisplayName("记录类型行：ITEM/OPTION/GLOBAL/STATION 首列与关键列正确")
    void rowShapes() {
        List<List<String>> rows = SyncCsvExporter.buildExport(SyncTestFixtures.catalog(), "ALL");
        assertEquals("ITEM", rows.get(1).get(0));
        assertEquals("data_source", rows.get(1).get(1));
        assertEquals("SINGLE_SELECT", rows.get(1).get(3));
        assertEquals("是", rows.get(1).get(4));

        List<String> optionRow = rows.stream().filter(row -> "OPTION".equals(row.get(0))).findFirst().orElseThrow();
        assertEquals("data_source", optionRow.get(1));
        assertEquals("DUODUOCAI", optionRow.get(10));

        List<String> globalRow = rows.stream()
                .filter(row -> "GLOBAL".equals(row.get(0)) && "collect_frequency".equals(row.get(1)))
                .findFirst().orElseThrow();
        assertEquals("EVERY_240M", globalRow.get(14));

        List<String> stationRow = rows.stream()
                .filter(row -> "STATION".equals(row.get(0)) && "城东驿站".equals(row.get(13)))
                .findFirst().orElseThrow();
        assertEquals("data_source", stationRow.get(1));
        assertEquals("DUODUOCAI", stationRow.get(14));
    }

    @Test
    @DisplayName("模板：表头 + 四类记录各一行示例")
    void template() {
        List<List<String>> rows = SyncCsvExporter.buildTemplate(SyncTestFixtures.catalog());
        assertEquals(5, rows.size());
        assertEquals(SyncConstants.EXPORT_HEADER, rows.get(0));
        assertEquals("ITEM", rows.get(1).get(0));
        assertEquals("OPTION", rows.get(2).get(0));
        assertEquals("GLOBAL", rows.get(3).get(0));
        assertEquals("STATION", rows.get(4).get(0));
    }

    @Test
    @DisplayName("文件名与范围校验")
    void filenameAndScope() {
        assertEquals(SyncConstants.TEMPLATE_FILENAME, SyncCsvExporter.exportFilename("TEMPLATE", LocalDate.now()));
        assertTrue(SyncCsvExporter.exportFilename("ALL", LocalDate.of(2026, 9, 24)).contains("20260924"));
        assertFalse(SyncCsvExporter.exportScopeInvalid(null));
        assertFalse(SyncCsvExporter.exportScopeInvalid(""));
        assertFalse(SyncCsvExporter.exportScopeInvalid("ALL"));
        assertTrue(SyncCsvExporter.exportScopeInvalid("NOPE"));
    }
}
