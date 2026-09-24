package com.qiujie.dto.finance;

import lombok.Data;

import java.util.List;

/**
 * 批量提交审核入参（对齐 Mock {@code submit}）：{@code ids} 须为非空数组。
 */
@Data
public class PayrollSubmitRequest {

    /** 工资单 id 列表（非空） */
    private List<Long> ids;
}
