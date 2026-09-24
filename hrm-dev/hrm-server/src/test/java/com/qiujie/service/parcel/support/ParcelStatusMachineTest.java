package com.qiujie.service.parcel.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 取件核销状态迁移单测（口径逐条对齐 Mock {@code parcelStore.js#pickupParcel}）。
 * <p>
 * 并发安全由 {@code ParcelMapper} 条件更新保证；本类只测「更新失败后的归因」分支。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class ParcelStatusMachineTest {

    private static final long ME = 5L;
    private static final long OTHER = 7L;

    @Test
    @DisplayName("在库待取（1）→ 可核销")
    void inStockCanPickup() {
        assertEquals(ParcelStatusMachine.PickupOutcome.SUCCESS,
                ParcelStatusMachine.decide(1, null, ME));
    }

    @Test
    @DisplayName("已取件且取件人非本人 → 7003（PICKED_BY_OTHER）")
    void pickedByOther() {
        assertEquals(ParcelStatusMachine.PickupOutcome.PICKED_BY_OTHER,
                ParcelStatusMachine.decide(2, OTHER, ME));
    }

    @Test
    @DisplayName("已取件且取件人为本人 / 未知 → 7002（状态不允许）")
    void pickedBySelfOrUnknown() {
        assertEquals(ParcelStatusMachine.PickupOutcome.STATUS_INVALID,
                ParcelStatusMachine.decide(2, ME, ME));
        assertEquals(ParcelStatusMachine.PickupOutcome.STATUS_INVALID,
                ParcelStatusMachine.decide(2, null, ME));
    }

    @Test
    @DisplayName("其它状态（0/3/4/空）→ 7002")
    void otherStatusesInvalid() {
        assertEquals(ParcelStatusMachine.PickupOutcome.STATUS_INVALID,
                ParcelStatusMachine.decide(0, null, ME));
        assertEquals(ParcelStatusMachine.PickupOutcome.STATUS_INVALID,
                ParcelStatusMachine.decide(3, null, ME));
        assertEquals(ParcelStatusMachine.PickupOutcome.STATUS_INVALID,
                ParcelStatusMachine.decide(4, null, ME));
        assertEquals(ParcelStatusMachine.PickupOutcome.STATUS_INVALID,
                ParcelStatusMachine.decide(null, null, ME));
    }
}
