package com.qiujie.vo.sync;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 选项出参（Mock {@code syncConfigStore.js} 的 optionSet.options 元素）。
 */
@Data
public class SyncConfigOptionVO {

    private String optionKey;
    private String label;
    /** 附加属性（按选项集约定） */
    private Map<String, Object> extraAttrs;
    private Integer sort;
    private Boolean enabled;
    private Boolean builtin;
    /** 来源：BUILTIN/MANUAL/MIGRATED */
    private String source;
    /** 兼容读取的旧码数组 */
    private List<String> legacyCodes;
    private String remark;
}
