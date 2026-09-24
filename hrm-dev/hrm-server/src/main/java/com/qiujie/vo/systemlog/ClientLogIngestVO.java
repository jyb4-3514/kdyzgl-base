package com.qiujie.vo.systemlog;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 批量上报结果（api.md §7.1 #14）：{@code accepted} = 本次实际接收条数（= 上报条数，指纹命中亦计入接收）。
 */
@Data
@AllArgsConstructor
public class ClientLogIngestVO {

    /** 本次接收的原始日志条数 */
    private int accepted;
}
