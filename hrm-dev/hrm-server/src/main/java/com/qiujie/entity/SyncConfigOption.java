package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.handler.SyncConfigExtraAttrsTypeHandler;
import com.qiujie.handler.SyncConfigLegacyCodesTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 选项集选项（db.md §8.9.5，DDL: V12__sync_config_center.sql）。
 * <p>
 * {@code setKey} 取 {@code data_source/collect_frequency/time_template}；{@code extraAttrs} 结构由选项集约定；
 * {@code legacyCodes} 是旧码 → 新码的兼容读取依据（映射关系放数据里，代码侧不硬编码映射表）。
 * 三态：{@code builtin} 内置不可删；{@code source}（BUILTIN/MANUAL/MIGRATED）记录来源；{@code enabled} 参与选择校验。
 */
@Data
@TableName(value = "sync_config_option", autoResultMap = true)
public class SyncConfigOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 选项集 Key */
    private String setKey;

    /** 选项 Key（集合内活跃唯一） */
    private String optionKey;

    /** 显示名（1-20 字） */
    private String label;

    /** 附加属性（按选项集约定） */
    @TableField(typeHandler = SyncConfigExtraAttrsTypeHandler.class)
    private Map<String, Object> extraAttrs;

    /** 排序（0-9999） */
    private Integer sort;

    /** 启用：0=停用，1=启用 */
    private Integer enabled;

    /** 系统内置：0=否（可删），1=是（仅可停用） */
    private Integer builtin;

    /** 来源：BUILTIN/MANUAL/MIGRATED */
    private String source;

    /** 兼容读取的旧码数组（迁移用） */
    @TableField(typeHandler = SyncConfigLegacyCodesTypeHandler.class)
    private List<String> legacyCodes;

    /** 备注（≤100 字） */
    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
