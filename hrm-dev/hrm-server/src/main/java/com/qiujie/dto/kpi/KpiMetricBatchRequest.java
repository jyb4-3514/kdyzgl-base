package com.qiujie.dto.kpi;

import lombok.Data;

import java.util.List;

/**
 * KPI 指标批量保存入参（api.md / Mock {@code saveMetricBatch}）。
 * <p>
 * 为什么需要批量接口：单指标接口每次都校验「启用指标合计 = 100%」，而调权重天然要多步
 * （30/20 → 40/10 的中间态都不等于 100%），逐条提交必然卡死。本接口把整组变更合成**一次原子提交**、
 * 只做一次合计校验，语义与单指标接口完全一致（启用合计必须为 100%）。
 */
@Data
public class KpiMetricBatchRequest {

    /** 变更项（非空数组） */
    private List<Item> items;

    /** 单项变更：id 必填，weight / enabled 至少改其一 */
    @Data
    public static class Item {
        /** 指标 id */
        private Long id;
        /** 新权重：0-100 整数（可选） */
        private Integer weight;
        /** 新启用状态：0/1（可选，同时接受 true/false；非法值 Service 统一回 400） */
        private Object enabled;
    }
}
