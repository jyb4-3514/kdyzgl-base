package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工资单列表分页出参（对齐 Mock {@code listPayrolls}）。
 * <p>
 * 在标准分页结构上额外带：
 * <ul>
 *   <li>{@code counts}：同月同（其它筛选口径）各状态计数，<b>不含 status 筛选</b>，
 *       否则切到某个标签页后其余计数全归零；前端标签页直接用，不必再拉全量自数；</li>
 *   <li>{@code month}：当前账期筛选（未传为 null）。</li>
 * </ul>
 */
@Data
public class PayrollPageVO {

    private long total;

    private long pageNum;

    private long pageSize;

    private List<PayrollVO> list;

    /** 六态计数（键为全部状态，保证前端标签页有稳定键序） */
    private Map<String, Integer> counts;

    /** 当前账期筛选（未传为 null） */
    private String month;
}
