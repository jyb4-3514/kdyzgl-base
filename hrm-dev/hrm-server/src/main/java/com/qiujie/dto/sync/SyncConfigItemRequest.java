package com.qiujie.dto.sync;

import lombok.Data;

import java.util.Map;

/**
 * 配置项新增 / 编辑入参（Mock {@code syncConfigStore.js#createItem / updateItem}）。
 * <p>
 * 编辑时 {@code valueType} 锁定（改类型会破坏存量值），{@code builtin} 不接受外部传入。
 * {@code constraints} / {@code defaultValue} 为类型化结构，由 Service 按 {@code valueType} 规范化与校验。
 */
@Data
public class SyncConfigItemRequest {

    /** 配置项 Key（新增必填；编辑忽略，取路径参数） */
    private String itemKey;

    /** 显示名（1-20 字） */
    private String name;

    /** 值类型：SINGLE_SELECT/NUMBER/TEXT/TIME/TIME_RANGE（新增必填；编辑锁定） */
    private String valueType;

    /** 驿站生效值是否必填 */
    private Boolean required;

    /** 默认值（类型化：NUMBER 传数字，其余传字符串；可为空） */
    private Object defaultValue;

    /** 单位（≤8 字） */
    private String unit;

    /** 取值约束（按值类型结构不同） */
    private Map<String, Object> constraints;

    /** 关联选项集（单选型缺省与 itemKey 同名） */
    private String optionSetKey;

    /** 生效范围：GLOBAL/STATION（缺省 STATION） */
    private String scope;

    /** 排序（0-9999 整数） */
    private Integer sort;

    /** 是否启用 */
    private Boolean enabled;

    /** 说明（≤100 字） */
    private String description;
}
