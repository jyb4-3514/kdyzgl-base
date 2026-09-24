package com.qiujie.dto.sync;

import lombok.Data;

/**
 * CSV 导入入参（Mock {@code syncConfigCenter.js#importConfig}）。
 * <p>
 * {@code dryRun=true} 只解析校验给预览，{@code false} 才落库；两步共用同一套解析逻辑。
 */
@Data
public class SyncConfigImportRequest {

    /** CSV 文本内容 */
    private String content;

    /** 冲突策略：OVERWRITE / SKIP / APPEND（缺省 OVERWRITE） */
    private String onConflict;

    /** 是否仅预览（true 不落库） */
    private Boolean dryRun;
}
