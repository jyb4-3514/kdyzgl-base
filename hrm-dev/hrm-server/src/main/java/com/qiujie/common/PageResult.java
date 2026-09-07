package com.qiujie.common;

import lombok.Data;

import java.util.List;

/**
 * 分页响应统一结构：{ total, pageNum, pageSize, list }（见 api.md 1.2）。
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.total = total;
        result.pageNum = pageNum;
        result.pageSize = pageSize;
        result.list = list;
        return result;
    }
}
