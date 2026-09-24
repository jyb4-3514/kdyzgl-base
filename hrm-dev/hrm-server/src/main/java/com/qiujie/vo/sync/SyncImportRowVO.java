package com.qiujie.vo.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV 导入行级预览（Mock {@code syncConfigCsv.js#rowLine}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncImportRowVO {

    private int rowNo;
    private String type;
    private String itemKey;
    private String optionKey;
    private String stationName;
    /** OK / WARNING / FAILED */
    private String level;
    private String message;
}
