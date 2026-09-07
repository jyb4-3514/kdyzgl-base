package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志表（只插不改不删，db.md 3.4 例外约定：不设 is_deleted/update_time）。
 * login_time 由代码显式赋值，不参与自动填充。
 */
@Data
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 尝试登录的账号名（登录失败时仍记录） */
    private String username;

    /** 员工 ID（账号存在时记录，历史留痕不校验） */
    private Long employeeId;

    /** 客户端 IP（Nginx 透传 X-Forwarded-For 首个） */
    private String loginIp;

    /** 登录结果：0=失败，1=成功 */
    private Integer loginResult;

    /** 失败原因（账号密码错误 / 账号已禁用） */
    private String failReason;

    /** 浏览器 UA（截断至 255） */
    private String userAgent;

    private LocalDateTime loginTime;
}
