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
 * 配置中心-全局默认值（db.md §8.9.6，DDL: V12__sync_config_center.sql）。
 * <p>
 * {@code value} 以字符串原样存储，按配置项 {@code valueType} 由 {@code SyncValueCodec} 转类型；
 * 空值 = 未配置，驿站走「未配置」判定链（不落覆盖）。
 */
@Data
@TableName("sync_config_global")
public class SyncConfigGlobal {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置项 Key（活跃唯一） */
    private String itemKey;

    /** 全局默认值（可为空） */
    private String value;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
