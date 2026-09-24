package com.qiujie.vo.sync;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务出参（Mock {@code routes/syncTask.js#toSyncTaskVO}）。
 */
@Data
public class SyncTaskVO {

    private Long id;
    private Long stationId;
    private String stationName;
    private String batchNo;
    /** 0=待领取 1=执行中 2=成功 3=失败 */
    private Integer status;
    private Integer parcelTotal;
    private Integer successCount;
    private Integer failCount;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime assignTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
