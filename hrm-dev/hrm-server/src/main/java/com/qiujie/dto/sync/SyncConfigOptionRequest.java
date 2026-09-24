package com.qiujie.dto.sync;

import lombok.Data;

import java.util.Map;

/**
 * 选项新增 / 编辑入参（Mock {@code syncConfigStore.js#createOption / updateOption}）。
 * <p>
 * 选项集由路径上的配置项 Key 解析（非单选型会被 Service 以 400 拦下）。
 * {@code extraAttrs} 结构由所属选项集约定（collect_frequency → intervalMinutes；time_template → startTime/endTime）。
 */
@Data
public class SyncConfigOptionRequest {

    /** 选项 Key（新增必填；编辑忽略，取路径参数） */
    private String optionKey;

    /** 显示名（1-20 字） */
    private String label;

    /** 附加属性（按选项集约定） */
    private Map<String, Object> extraAttrs;

    /** 排序（0-9999 整数） */
    private Integer sort;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注（≤100 字） */
    private String remark;
}
