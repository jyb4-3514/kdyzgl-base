package com.qiujie.vo.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV 导入汇总（Mock {@code syncConfigCsv.js#buildImportPlan} 的 summary）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncImportSummaryVO {

    private int total;
    private int ok;
    private int failed;
    private int warning;
}
