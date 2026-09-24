package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务日志（追加型，db.md §8.9.2，DDL: V11__sync_task.sql）。
 * <p>
 * 追加型数据无更新与删除语义，故<b>不设</b> {@code is_deleted} / {@code update_time}；
 * 业务时间即 {@code logTime}。
 */
@Data
@TableName("sync_task_log")
public class SyncTaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 同步任务（逻辑外键 sync_task.id） */
    private Long taskId;

    /** 批次号快照 */
    private String batchNo;

    /** 级别：0=INFO 1=WARN 2=ERROR */
    private Integer level;

    /** 日志内容 */
    private String message;

    /** 日志时间（只插不改） */
    private LocalDateTime logTime;
}
