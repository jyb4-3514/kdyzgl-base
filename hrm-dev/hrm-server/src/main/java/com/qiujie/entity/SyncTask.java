package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务（状态机四态，db.md §8.9.1，DDL: V11__sync_task.sql）。
 * <p>
 * 状态：0=待领取 1=执行中 2=成功 3=失败；仅「失败」可重试（回到 0 且 retry_count+1）。
 * {@code batchNo} 是采集端轮询与数据回推的对接键（活跃唯一，Service 查重）。
 */
@Data
@TableName("sync_task")
public class SyncTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 批次号（活跃唯一，采集/回推对接键） */
    private String batchNo;

    /** 状态：0=待领取 1=执行中 2=成功 3=失败 */
    private Integer status;

    /** 本批包裹总数 */
    private Integer parcelTotal;

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failCount;

    /** 已重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMsg;

    /** 领取时间 */
    private LocalDateTime assignTime;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
