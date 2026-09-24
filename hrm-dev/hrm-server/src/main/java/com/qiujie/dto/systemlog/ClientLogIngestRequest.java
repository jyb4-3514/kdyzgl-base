package com.qiujie.dto.systemlog;

import lombok.Data;

import java.util.List;

/**
 * 运行日志批量上报入参（api.md §7.1 #14）。
 * <p>
 * 不做 Bean Validation 声明（非空/上限）：Mock 的判定文案（「logs 须为非空数组」「单批日志不超过 100 条」）
 * 需与前端提示逐字一致，故在 Service 内显式校验并给出同款文案。
 */
@Data
public class ClientLogIngestRequest {

    /** 批量日志，单批 ≤100 且非空 */
    private List<ClientLogItem> logs;
}
