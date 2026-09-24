package com.qiujie.vo.sync;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务日志出参（Mock {@code routes/syncTask.js#logs}）。
 */
@Data
public class SyncTaskLogVO {

    private Long id;
    private Long taskId;
    private String batchNo;
    /** 0=INFO 1=WARN 2=ERROR */
    private Integer level;
    private String message;
    private LocalDateTime logTime;
}
