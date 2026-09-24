package com.qiujie.vo.sync;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集状态看板的单站行（Mock {@code routes/syncConfig.js#overview} 的 stations 元素）。
 */
@Data
public class SyncStationStateVO {

    private Long stationId;
    private String stationName;
    private String collectState;
    private String collectStateLabel;
    private Boolean enabled;
    /** 数据源显示名 */
    private String dataSource;
    private String lastCollectStatus;
    private LocalDateTime lastCollectTime;
    private SyncLastBatchVO lastBatch;
}
