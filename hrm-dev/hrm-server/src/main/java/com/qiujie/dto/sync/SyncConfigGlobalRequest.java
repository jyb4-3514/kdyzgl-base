package com.qiujie.dto.sync;

import lombok.Data;

import java.util.Map;

/**
 * 全局默认值保存入参（Mock {@code syncConfigCenter.js#saveGlobalConfig}：局部更新，只提交改动项）。
 */
@Data
public class SyncConfigGlobalRequest {

    /** itemKey → 类型化值（仅提交改动项，避免把种子里的空值重新校验一遍） */
    private Map<String, Object> values;
}
