package com.qiujie.vo.sync;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 配置项出参（Mock {@code syncConfigStore.js#listConfig} 的 items 元素）。
 */
@Data
public class SyncConfigItemVO {

    private String itemKey;
    private String name;
    private String description;
    private String valueType;
    private Boolean required;
    /** 类型化默认值（NUMBER 为数字，其余为字符串） */
    private Object defaultValue;
    private String unit;
    private Map<String, Object> constraints;
    private String optionSetKey;
    private String scope;
    private Integer sort;
    private Boolean enabled;
    private Boolean builtin;
    private LocalDateTime updateTime;
}
