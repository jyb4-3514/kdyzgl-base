package com.qiujie.dto.leave;

import lombok.Data;

/**
 * 撤回入参（api.md §7.1 #13）。撤回等于撤销一次已生效的公司决定，原因必填 2-100 字。
 */
@Data
public class LeaveRevokeRequest {

    /** 撤回原因（2-100 字） */
    private String reason;
}
