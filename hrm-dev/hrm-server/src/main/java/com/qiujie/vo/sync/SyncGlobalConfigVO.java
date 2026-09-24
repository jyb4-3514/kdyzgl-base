package com.qiujie.vo.sync;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局默认值出参（Mock {@code syncConfigCenter.js#getGlobalConfig}：{ values }）。
 */
@Data
public class SyncGlobalConfigVO {

    /** itemKey → 类型化值 */
    private Map<String, Object> values = new LinkedHashMap<>();
}
