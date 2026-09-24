package com.qiujie.vo.notification;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 未读通知数出参（api.md §7.1）：{@code count} = 当前登录人的未读数。
 */
@Data
@AllArgsConstructor
public class UnreadCountVO {

    /** 未读条数 */
    private long count;
}
