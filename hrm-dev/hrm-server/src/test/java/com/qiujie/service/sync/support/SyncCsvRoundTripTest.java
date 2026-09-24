package com.qiujie.service.sync.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV「导出即导入」闭环一致性单测（设计 D 章核心断言）。
 * <p>
 * 断言链：① 导出 → 序列化 → 解析 与原行逐行等价（文本层往返一致）；
 * ② 导出的 ALL 文件以 OVERWRITE 导回 → 0 FAILED、0 CREATE、0 CONFLICT（语义层往返一致，全为已存在更新）；
 * ③ 同一文件以 SKIP 导回 → 全 SKIP（冲突策略生效）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncCsvRoundTripTest {

    @Test
    @DisplayName("文本层往返：parse(serialize(exportAll)) 与导出行逐行等价")
    void textRoundTripStable() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        List<List<String>> exported = SyncCsvExporter.buildExport(catalog, "ALL");
        List<List<String>> parsed = SyncCsvCodec.parse(SyncCsvCodec.serialize(exported));
        assertEquals(exported.size(), parsed.size());
        for (int i = 0; i < exported.size(); i++) {
            assertEquals(new ArrayList<>(exported.get(i)), parsed.get(i), "第 " + (i + 1) + " 行往返不一致");
        }
    }

    @Test
    @DisplayName("语义层往返：导出 ALL → OVERWRITE 导回 0 失败 0 新增；SKIP 导回全跳过")
    void semanticRoundTrip() {
        SyncConfigCatalog catalog = SyncTestFixtures.catalog();
        String content = SyncCsvCodec.serialize(SyncCsvExporter.buildExport(catalog, "ALL"));

        SyncCsvImporter.PlanOutcome overwrite =
                SyncCsvImporter.buildImportPlan(content, "OVERWRITE", catalog, 1000);
        assertTrue(overwrite.isOk());
        assertEquals(0, overwrite.data().summary().failed());
        assertEquals(0, overwrite.data().plan().create());
        assertEquals(0, overwrite.data().plan().conflict());
        assertTrue(overwrite.data().plan().update() > 0);

        SyncCsvImporter.PlanOutcome skip = SyncCsvImporter.buildImportPlan(content, "SKIP", catalog, 1000);
        assertTrue(skip.isOk());
        assertEquals(overwrite.data().summary().total(), skip.data().plan().skip());
    }
}
