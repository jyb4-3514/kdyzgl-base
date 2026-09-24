package com.qiujie.dto.finance;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的工资单查询（任意角色，对齐 Mock {@code myList}）。
 * <p>
 * 只返回本人且仅已发布/已确认；{@code status} 若传，取值须为 PUBLISHED / CONFIRMED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyPayrollQuery extends PageQuery {

    /** 账期 yyyy-MM（可空） */
    private String month;

    /** 状态（可空；仅 PUBLISHED / CONFIRMED） */
    private String status;
}
