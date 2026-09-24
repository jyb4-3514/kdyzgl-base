package com.qiujie.vo.parcel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qiujie.common.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 包裹分页出参：在统一分页结构 {@code {total, pageNum, pageSize, list}} 之上**可选**附加 {@code nextCursor}。
 * <p>
 * 默认（未使用游标翻页）{@code nextCursor} 为 null → 序列化时省略，结构<b>与 Mock 逐位一致</b>；
 * 仅当调用方显式传入 {@code cursor}（opt-in 契约扩展）时回填，供前端 keyset 翻页。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ParcelPageVO extends PageResult<ParcelVO> {

    /** 下一页游标（当前页取满时的末行键；未取满或未使用游标时为 null → 省略） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nextCursor;

    public static ParcelPageVO of(long total, long pageNum, long pageSize, List<ParcelVO> list,
                                  String nextCursor) {
        ParcelPageVO result = new ParcelPageVO();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setList(list);
        result.setNextCursor(nextCursor);
        return result;
    }
}
