package com.qiujie.vo.notification;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发布通知结果（api.md §7.1 / Mock {@code publish}）：{@code count} = 实际生成的收件人数。
 */
@Data
@AllArgsConstructor
public class PublishResultVO {

    /** 实际生成的通知条数（= 命中的在职且启用员工数） */
    private int count;
}
