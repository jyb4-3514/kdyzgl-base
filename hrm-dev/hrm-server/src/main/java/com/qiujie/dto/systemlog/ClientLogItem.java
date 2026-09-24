package com.qiujie.dto.systemlog;

import lombok.Data;

/**
 * 单条上报日志（api.md §7.5 白名单入参）。
 * <p>
 * 为什么全部声明为 {@code String}：上报数据是不可信客户端遥测，Mock 用 {@code Number()} 宽容处理数字字段；
 * Jackson 会把 JSON 数字自动转成 String，故 String 声明既能接受 {@code 500} 与 {@code "500"}，
 * 又不会因类型不符导致整批 400。数值字段在 {@link com.qiujie.service.support.ClientLogSanitizer} 内宽容解析。
 * <p>
 * 白名单复制：未列出的字段（token / password / 身份证 / 银行卡 / 请求响应体原文等）因本 DTO 无对应属性
 * 而不会被绑定，天然丢弃——等价 Mock 的「只复制允许字段」。
 * <p>
 * 刻意<b>不含 employeeId</b>：上报人一律以登录身份填充（防伪造上报归属），不接受前端传入。
 */
@Data
public class ClientLogItem {

    /** 日志发生时间（可空，缺省取服务端当前时间） */
    private String time;

    /** 级别：INFO/WARN/ERROR（非法值归一为 ERROR） */
    private String level;

    /** 上报端：PC/H5/SHELL（非法值归一为 PC） */
    private String source;

    /** 前端路由 */
    private String route;

    /** 日志内容 */
    private String message;

    /** 错误堆栈 */
    private String stack;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String path;

    /** HTTP 状态码 */
    private String status;

    /** 业务码 */
    private String code;

    /** 耗时（毫秒） */
    private String duration;

    /** 浏览器 UA */
    private String ua;
}
