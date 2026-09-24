package com.qiujie.vo.systemlog;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 清空运行日志结果（api.md §7.1 #16）：{@code cleared} = 清空前的总条数。
 */
@Data
@AllArgsConstructor
public class ClientLogClearVO {

    /** 本次物理删除的条数 */
    private long cleared;
}
