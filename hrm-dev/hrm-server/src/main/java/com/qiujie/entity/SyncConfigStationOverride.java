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
 * 配置中心-驿站覆盖值（db.md §8.9.7，DDL: V12__sync_config_center.sql）。
 * <p>
 * 一行 = 某驿站对某配置项的覆盖；(station_id, item_key) 活跃唯一。删除配置项/选项后必须清理命中的覆盖行，
 * 否则会残留指向已删对象的悬空覆盖。
 */
@Data
@TableName("sync_config_station_override")
public class SyncConfigStationOverride {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 配置项 Key */
    private String itemKey;

    /** 覆盖值（可为空） */
    private String value;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
