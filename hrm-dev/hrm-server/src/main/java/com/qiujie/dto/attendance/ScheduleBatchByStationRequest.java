package com.qiujie.dto.attendance;

import lombok.Data;

import java.util.List;

/**
 * 整站批量排班入参（POST /schedules/batch-by-station，仅 ADMIN）。
 * <p>
 * 手动模式（{@code shiftId} 给定）：把「整站员工 × 日期范围（可只排指定星期）」铺上同一班次（Mock 语义，逐位保留）。
 * 智能模式（{@code shiftId} 缺省）：改由 S3 算法（贪心构造 + 模拟退火）逐格生成班次，尊重「每日每班最少在岗 /
 * 连续工作上限 / 轮休均衡 / 班次均衡」约束，失败降级返回贪心解 + 违规清单。
 * <p>
 * TODO(扩展): 智能模式为对既有端点的<b>可选扩展</b>（手动模式入出参与 Mock 完全一致）；
 *   如需把智能排班固化为正式契约，请同步 {@code api.md} 后再移除本注释。
 */
@Data
public class ScheduleBatchByStationRequest {

    private Long stationId;

    /** 缺省 = 智能排班模式（S3 算法） */
    private Long shiftId;

    /** 起始日期 yyyy-MM-dd */
    private String startDate;

    /** 结束日期 yyyy-MM-dd */
    private String endDate;

    /** 参与员工（缺省 = 该驿站全部在职员工） */
    private List<Long> employeeIds;

    /** 已存在排班是否跳过（缺省 true；false 表示覆盖） */
    private Boolean skipExisting;

    /** 只排命中的星期（0=周日 … 6=周六，与 JS getDay 一致；缺省 = 范围每天） */
    private List<Integer> weekdays;
}
