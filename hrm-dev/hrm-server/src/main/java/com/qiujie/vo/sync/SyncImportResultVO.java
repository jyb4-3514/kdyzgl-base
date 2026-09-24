package com.qiujie.vo.sync;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 导入出参（Mock {@code syncConfigCenter.js#importConfig}）。
 * <p>
 * dryRun=true 只解析校验给预览（{@code applied} 为 null 且<b>不输出该键</b>，与 Mock 的字段展开一致）；
 * false 才落库并回填 {@code applied}。
 */
@Data
public class SyncImportResultVO {

    private Boolean dryRun;
    private SyncImportSummaryVO summary;
    private List<SyncImportRowVO> rows = new ArrayList<>();
    private SyncImportPlanVO plan;
    /** 落库结果；dryRun 时为 null（序列化时省略该键，对齐 Mock） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SyncImportAppliedVO applied;
}
