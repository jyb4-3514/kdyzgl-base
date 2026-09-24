package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.handler.SyncConfigConstraintsTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 配置项定义（db.md §8.9.4，DDL: V12__sync_config_center.sql）。
 * <p>
 * 三态语义不混淆：{@code scope}（GLOBAL/STATION）决定可否按驿站覆盖；{@code builtin}=1 表示系统内置，
 * 只可停用不可删除；{@code enabled} 控制是否参与生效值计算。
 * {@code constraints} 为 JSON 列，结构随 {@code valueType} 变化（低频读取），故用 {@code Map} 承接，
 * 由 {@code SyncConfigValidator} 按类型解释。
 * <p>
 * {@code autoResultMap = true}：JSON 列必须开启结果映射，否则类型处理器不生效。
 */
@Data
@TableName(value = "sync_config_item", autoResultMap = true)
public class SyncConfigItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置项 Key（活跃唯一，小写字母开头） */
    private String itemKey;

    /** 显示名（1-20 字） */
    private String name;

    /** 说明（≤100 字） */
    private String description;

    /** 值类型：SINGLE_SELECT/NUMBER/TEXT/TIME/TIME_RANGE */
    private String valueType;

    /** 驿站生效值是否必填：0=否，1=是 */
    private Integer required;

    /** 默认值（原样存储，按 valueType 由服务层转换） */
    private String defaultValue;

    /** 单位（≤8 字） */
    private String unit;

    /** 取值约束（按 valueType 结构不同） */
    @TableField(typeHandler = SyncConfigConstraintsTypeHandler.class)
    private Map<String, Object> constraints;

    /** 关联选项集（单选型必填） */
    private String optionSetKey;

    /** 生效范围：GLOBAL/STATION */
    private String scope;

    /** 排序（0-9999） */
    private Integer sort;

    /** 启用：0=停用，1=启用 */
    private Integer enabled;

    /** 系统内置：0=否（可删），1=是（仅可停用） */
    private Integer builtin;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
