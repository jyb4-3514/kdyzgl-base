package com.qiujie.service.finance.support;

import java.math.BigDecimal;

/**
 * 工资单项「草稿」（解析完成、即将落库的明细行）。
 * <p>
 * 为什么单独承载：解析结果 {@link PayrollItemAmount} 只有金额与文案，
 * 合计口径还需要 {@code type}（增/扣）；两者组合成本记录即「算薪内核 → 合计 → 落库」的统一中间态。
 */
public record PayrollItemDraft(String key,
                               String name,
                               String type,
                               String source,
                               BigDecimal amount,
                               String detail) {
}
