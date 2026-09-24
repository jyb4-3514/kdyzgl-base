package com.qiujie.vo.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV 导入落库结果（Mock {@code syncConfigCsv.js#applyImport}：仅非 dryRun 返回）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncImportAppliedVO {

    private int created;
    private int updated;
    private int skipped;
}
