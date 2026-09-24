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
 * 驿站采集配置（一驿一行，db.md §8.9.3，DDL: V12__sync_config_center.sql）。
 * <p>
 * 语义区分两处开关：{@code enabled}=采集开关；{@code status}=配置行是否停用。
 * 二者与 {@code lastCollectStatus} 共同派生四种采集状态（DISABLED / UNCONFIGURED / ABNORMAL / NORMAL）。
 * {@code frequency} / {@code dataSource} 为兼容快照列，生效值由配置中心四层模型派生（见 Service）。
 */
@Data
@TableName("sync_station_config")
public class SyncStationConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id，一驿一行） */
    private Long stationId;

    /** 采集开关：0=关闭，1=开启 */
    private Integer enabled;

    /** 采集频率码（旧码或迁移后的选项 Key，兼容快照列） */
    private String frequency;

    /** 数据源（选项 Key，兼容快照列） */
    private String dataSource;

    /** 采集开始（HH:mm） */
    private String collectStartTime;

    /** 采集结束（HH:mm，可 24:00） */
    private String collectEndTime;

    /** 最近采集时间 */
    private LocalDateTime lastCollectTime;

    /** 最近采集状态：SUCCESS / FAILED / NEVER */
    private String lastCollectStatus;

    /** 配置行状态：0=停用，1=启用 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
