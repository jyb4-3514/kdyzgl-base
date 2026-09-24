package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端运行日志（db.md §7.3 / §8.1，DDL: V3__client_log.sql）。
 * <p>
 * 追加型表：只插不改（同指纹在去重窗口内累加 {@code count}）、清空=物理删除，故<b>无</b>
 * {@code is_deleted} / {@code update_time}，业务时间字段为 {@code time}（沿用 login_log 的日志表例外约定）。
 * 时间字段由 Service 显式填入，不走 MetaObjectHandler 自动填充（本表无 create_time/update_time 列）。
 */
@Data
@TableName("client_log")
public class ClientLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 日志发生时间（客户端上报的业务时间；缺省/不可解析时取服务端当前时间） */
    private LocalDateTime time;

    /** 级别：INFO/WARN/ERROR（非法值入库前归一为 ERROR） */
    private String level;

    /** 上报端：PC/H5/SHELL（非法值入库前归一为 PC） */
    private String source;

    /** 上报人（入库时以登录身份填充，见 ClientLogServiceImpl；不接收前端传入，防伪造归属） */
    private Long employeeId;

    /** 前端路由 */
    private String route;

    /** 日志内容（白名单脱敏 + 凭据擦除 + textMax 截断后的结果） */
    private String message;

    /** 错误堆栈（同上脱敏与截断） */
    private String stack;

    /** HTTP 方法 */
    private String method;

    /** 请求路径（已去 query/hash，防凭据随路径入库） */
    private String path;

    /** HTTP 状态码 */
    private Integer status;

    /** 业务码（指纹组成之一） */
    private Integer code;

    /** 耗时（毫秒） */
    private Integer duration;

    /** 浏览器 UA */
    private String ua;

    /** 指纹去重窗口内累计出现次数（S8，窗口判定在 Service 内完成） */
    private Integer count;

    /** 首次出现时间 */
    private LocalDateTime firstTime;

    /** 最近出现时间（同时作为去重窗口锚点，见 ClientLogServiceImpl） */
    private LocalDateTime lastTime;
}
