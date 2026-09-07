package com.qiujie.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 登录会话数据（key = hrm:session:{employeeId}，见 api.md 3.3）。
 * 注意：字段全部使用 String，避免 JSON 序列化时序号兼容问题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {

    private String jti;
    private String username;
    private String role;
    private String loginIp;
    /** 登录时间（yyyy-MM-dd HH:mm:ss） */
    private String loginTime;
}
