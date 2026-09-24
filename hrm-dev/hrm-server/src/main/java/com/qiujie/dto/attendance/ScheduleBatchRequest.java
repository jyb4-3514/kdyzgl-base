package com.qiujie.dto.attendance;

import lombok.Data;

import java.util.List;

/**
 * 手动批量排班入参（POST /schedules/batch，仅 ADMIN）。
 * <p>
 * 唯一性 = {@code employeeId + workDate}；同一员工同一天重复提交即覆盖；
 * {@code shiftId} 传空表示清空该天排班。单次 ≤200 条（防整月矩阵一次提交卡顿）。
 */
@Data
public class ScheduleBatchRequest {

    private Long stationId;

    private List<Item> items;

    /** 单条排班项 */
    @Data
    public static class Item {
        private Long employeeId;
        /** yyyy-MM-dd */
        private String workDate;
        /** 空/缺省表示清空该天排班 */
        private Long shiftId;
    }
}
