package com.qiujie.vo.workorder;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 工单详情出参（对齐 Mock {@code toWorkOrderDetailVO}）：在列表 VO 之上补转单留痕。
 * <p>
 * 列表不带 {@code transfers}（避免列表平白多一份嵌套数据，Mock 注释原话），仅详情返回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderDetailVO extends WorkOrderVO {

    /** 转单留痕（按转单时间倒序，就近在上） */
    private List<WorkOrderTransferVO> transfers;
}
