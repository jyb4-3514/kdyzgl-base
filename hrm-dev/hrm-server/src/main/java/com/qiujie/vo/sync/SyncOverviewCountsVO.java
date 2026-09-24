package com.qiujie.vo.sync;

import lombok.Data;

/**
 * 采集状态四态计数（Mock {@code routes/syncConfig.js#overview} 的 counts）。
 */
@Data
public class SyncOverviewCountsVO {

    private int normal;
    private int abnormal;
    private int unconfigured;
    private int disabled;
}
