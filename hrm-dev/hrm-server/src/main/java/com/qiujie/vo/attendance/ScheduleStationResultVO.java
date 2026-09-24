package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 整站批量排班结果（对齐 Mock {@code saveSchedulesByStation} 返回 {created, skipped, total}）。
 * <p>
 * {@code violations} 仅在<b>智能排班模式</b>（未指定 shiftId、由 S3 算法生成）下返回；
 * 手动铺同一班次时保持 null 并由 {@link JsonInclude} 省略，使手动模式的出参与 Mock 完全一致。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleStationResultVO {

    /** 新增/覆盖的「人 × 日」条数 */
    private int created;

    /** 因已存在排班而跳过的条数（仅 skipExisting=true 时可能非 0） */
    private int skipped;

    /** 参与判定的「人 × 日」组合数 = created + skipped */
    private int total;

    /** 算法模式下的违规清单（降级信息），手动模式为 null */
    private List<ScheduleViolationVO> violations;

    /** 是否走了失败降级（贪心解 + 违规清单） */
    private Boolean fallback;
}
