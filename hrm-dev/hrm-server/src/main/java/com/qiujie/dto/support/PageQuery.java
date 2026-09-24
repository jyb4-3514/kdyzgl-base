package com.qiujie.dto.support;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类（对齐 api.md 1.1 与 Mock {@code domain/pagination.js} / {@code validate.pageSizeInvalid}）。
 * <ul>
 *   <li>{@code pageNum} 缺省 1，须 ≥1（<1 或非数字 → HTTP 200 + code 400，不再静默钳制）；无上限，
 *       越界页返回空 list 但保留 total（与 Mock {@code paginate} 及 SQL LIMIT 行为一致）；</li>
 *   <li>{@code pageSize} 缺省 10，有效区间 [1,100]，越界或非数字 → HTTP 200 + code 400
 *       （文案「每页条数须为 1-100」与 Mock 逐字一致）。</li>
 * </ul>
 * 为什么改为报错而非钳制：契约（Mock）对越界 pageSize 返回 400，钳制会让前端拿到与契约不符的静默结果。
 * 用法：分页端点入参 DTO 继承本类，并在控制器参数上加 {@code @Valid} 触发校验。
 */
@Data
public class PageQuery {

    /** 页码，缺省 1 */
    @Min(value = 1, message = "页码须为正整数")
    private Integer pageNum = 1;

    /** 每页条数，缺省 10，区间 [1,100] */
    @Min(value = 1, message = "每页条数须为 1-100")
    @Max(value = 100, message = "每页条数须为 1-100")
    private Integer pageSize = 10;
}
