package com.qiujie.vo.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV 导入计划计数（Mock {@code syncConfigCsv.js} 的 plan）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncImportPlanVO {

    private int create;
    private int update;
    private int skip;
    /** 因「追加策略不允许覆盖」而失败的行数 */
    private int conflict;
}
