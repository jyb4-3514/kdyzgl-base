package com.qiujie.service.parcel;

import com.qiujie.dto.parcel.ParcelQuery;
import com.qiujie.dto.parcel.ParcelRankingQuery;
import com.qiujie.dto.parcel.ParcelTrendQuery;
import com.qiujie.vo.parcel.ParcelPageVO;
import com.qiujie.vo.parcel.ParcelRankingVO;
import com.qiujie.vo.parcel.ParcelSummaryVO;
import com.qiujie.vo.parcel.ParcelTrendPointVO;
import com.qiujie.vo.parcel.ParcelVO;

import java.util.List;

/**
 * 包裹服务（M10，6 接口，api.md / Mock {@code routes/parcel.js}，架构 §6.2 P10）。
 * <p>
 * 数据范围：列表按 L1 收敛（非 ADMIN 强制本人驿站）；summary/trend/ranking 按角色收敛（ADMIN 全站）；
 * 详情/取件逐端点越权（详情跨站 404、取件跨站 7001）。
 */
public interface ParcelService {

    /** 分页列表（默认游标分页；OFFSET 兜底并限最大页深） */
    ParcelPageVO page(ParcelQuery query);

    /** 看板指标（由 S7-1 预测能力不改变既有字段；空表返回全零） */
    ParcelSummaryVO summary();

    /** 近 N 天入库/取件趋势（S7-1 预测作为可选附加字段） */
    List<ParcelTrendPointVO> trend(ParcelTrendQuery query);

    /** 驿站排行（S7-2 容量/热力作为可选附加字段） */
    List<ParcelRankingVO> ranking(ParcelRankingQuery query);

    /** 包裹详情（跨站 → 404） */
    ParcelVO detail(Long id);

    /** 取件核销（7001 不存在 / 7002 状态非法 / 7003 已被他人取） */
    ParcelVO pickup(Long id);
}
