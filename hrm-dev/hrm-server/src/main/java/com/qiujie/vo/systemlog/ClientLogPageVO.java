package com.qiujie.vo.systemlog;

import com.qiujie.common.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运行日志分页出参（api.md §7.1 #15）：分页结构 + {@code counts} 统计。
 * <p>
 * 复用 {@link PageResult} 的 {@code total/pageNum/pageSize/list}，追加 {@code counts}；
 * counts 口径为「当前筛选结果」（不含分页），与 Mock {@code queryClientLogs} 及 PC 界面提示一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClientLogPageVO extends PageResult<ClientLogVO> {

    /** 当前筛选结果统计（不含分页） */
    private Counts counts;

    /** 统计计数 */
    @Data
    public static class Counts {
        /** 当前筛选结果总条数 */
        private long total;
        /** ERROR 条数 */
        private long error;
        /** WARN 条数 */
        private long warn;
        /** INFO 条数 */
        private long info;
        /** 涉及上报端数（distinct source） */
        private long sourceCount;
    }
}
