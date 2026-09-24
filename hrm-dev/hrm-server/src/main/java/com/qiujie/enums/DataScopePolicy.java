package com.qiujie.enums;

/**
 * L3 路径资源越权策略（对齐 Mock 逐端点 403/404/静默口径，见架构 1.4.2 实测表与 ADR-07）。
 * <p>
 * <b>禁止统一</b>：同一模块内 403 与 404 混用是契约的一部分（例如 {@code /sync-tasks/{id}} 详情 404、
 * 而 {@code /sync-tasks/{id}/trigger} 触发为 403），必须逐端点声明。
 */
public enum DataScopePolicy {

    /** 不适用：列表/统计类端点为「静默收敛」，路径资源不做归属校验（放行由查询层收敛保证） */
    SILENT,

    /** 跨站/无权访问按「资源不存在」返回 HTTP 404 + code 404（如 parcels、work-orders、sync-tasks 详情） */
    NOT_FOUND,

    /** 跨站/无权访问返回 HTTP 403 + code 403（如 kpi/scores、hr/profiles、sync-tasks 触发/重试） */
    FORBIDDEN
}
