package com.qiujie.vo.sync;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 该站最近一个同步批次（Mock {@code routes/syncConfig.js#lastBatchOf}）。
 */
@Data
public class SyncLastBatchVO {

    private String batchNo;
    private Integer status;
    private Integer parcelTotal;
    private Integer successCount;
    private Integer failCount;
    private LocalDateTime createTime;
}
