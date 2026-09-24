package com.qiujie.service.kpi;

import com.qiujie.dto.kpi.KpiCalculateRequest;
import com.qiujie.dto.kpi.KpiRankingQuery;
import com.qiujie.dto.kpi.KpiScoreQuery;
import com.qiujie.vo.kpi.KpiCalculateResultVO;
import com.qiujie.vo.kpi.KpiRankingPageVO;
import com.qiujie.vo.kpi.KpiScoreDetailVO;
import com.qiujie.vo.kpi.KpiScorePageVO;

/**
 * KPI 算分与评分查询服务（4 接口：calculate / ranking / scores / scores/{employeeId}）。
 * <p>
 * 算分只纳入「在职且启用」员工（{@code status=1}），与 Mock 一致；同月同员工**覆盖重建**（幂等）。
 * 越权：{@code scores/{employeeId}} 员工查他人 → 403、站长查他站 → 403；{@code scores/ranking} 非 ADMIN 静默收敛。
 */
public interface KpiScoreService {

    /** 按月算分（仅 ADMIN；覆盖重建，幂等） */
    KpiCalculateResultVO calculate(KpiCalculateRequest request);

    /** 排名榜（ADMIN/STATION_ADMIN） */
    KpiRankingPageVO ranking(KpiRankingQuery query);

    /** 得分列表（ADMIN/STATION_ADMIN） */
    KpiScorePageVO list(KpiScoreQuery query);

    /** 某员工某月得分明细（ALL；员工越权查他人 / 站长跨站 → 403） */
    KpiScoreDetailVO detail(Long employeeId, String month);
}
