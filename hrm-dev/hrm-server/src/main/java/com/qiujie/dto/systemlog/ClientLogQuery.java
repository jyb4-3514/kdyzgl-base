package com.qiujie.dto.systemlog;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运行日志查询条件（api.md §7.1 #15，查询参数绑定，仅 ADMIN）。
 * <p>
 * 继承 {@link PageQuery} 获得 {@code pageNum}/{@code pageSize}（含越界→400 校验）；
 * 本端点不按驿站收敛（ADMIN 专属，且日志无业务归属），故不继承 StationScopedQuery。
 * {@code startTime/endTime} 保持字符串形态（Mock 以字符串比较时间；日期格式校验见 ClientLogQueryValidator）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClientLogQuery extends PageQuery {

    /** 级别筛选：INFO/WARN/ERROR */
    private String level;

    /** 上报端筛选：PC/H5/SHELL */
    private String source;

    /** 错误信息模糊匹配（case-insensitive contains） */
    private String keyword;

    /** 起始时间，形如 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 结束时间，形如 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss */
    private String endTime;

    /** 按上报人筛选（ADMIN 排障用） */
    private Long employeeId;
}
