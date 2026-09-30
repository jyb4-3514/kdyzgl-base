package com.qiujie.dto.attendance;

import lombok.Data;

import java.util.List;

/**
 * 手动批量排班入参（POST /schedules/batch，仅 ADMIN）。
 * <p>
 * 语义（ARCH-C-2，主代理裁定 A-④）：以「当天班次集合」<b>整体覆盖</b>当前活跃排班（非累加）。
 * {@code shiftIds} 存在即为准：空数组=清空该天；仅传 {@code shiftId} 视为单元素集合；两者皆缺=清空（沿用现行）。
 * 单次 ≤200 条（防整月矩阵一次提交卡顿）；单日班次上限见 {@code hrm.algo.attendance.maxShiftsPerDay}。
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
        /**
         * 目标班次集合：空数组=清空该天；缺省时回退 {@link #shiftId}，两者皆缺=清空。
         * 整批原子；重复班次按 {@code duplicateShiftPolicy} 幂等处理。
         */
        private List<Long> shiftIds;
        /**
         * 单值班次（<b>弃用，仅兼容旧客户端</b>；{@code shiftIds} 存在时以 {@code shiftIds} 为准）。
         */
        private Long shiftId;
    }
}
