package com.qiujie.vo.parcel;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 趋势单日点（对齐 Mock {@code parcelStore.js#parcelTrend}，元素结构 {@code {date, inbound, pickup}}）。
 * <p>
 * {@code forecastInbound} 为 **S7-1 契约扩展字段**：仅当 {@code hrm.algo.parcel.forecast.appendForecast=true}
 * 时输出（模型拟合/一步预测值）；默认关闭，出参与 Mock 逐位一致（数组长度与既有字段均不变）。
 */
@Data
public class ParcelTrendPointVO {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 当日入库件数 */
    private long inbound;

    /** 当日取件件数 */
    private long pickup;

    /** 预测/拟合入库量（可选扩展；默认不输出） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double forecastInbound;
}
