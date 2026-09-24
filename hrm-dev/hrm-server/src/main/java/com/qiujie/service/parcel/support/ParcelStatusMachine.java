package com.qiujie.service.parcel.support;

/**
 * 取件核销状态迁移（纯函数，口径逐条对齐 Mock {@code parcelStore.js#pickupParcel}）。
 * <p>
 * 判定顺序：
 * <ol>
 *   <li>{@code status = 1}（在库待取）→ 可核销；</li>
 *   <li>{@code status = 2}（已取件）且取件人非本人 → 7003（并发/他人已取）；</li>
 *   <li>其余（0/2 本人/3/4/空）→ 7002（状态不允许取件）。</li>
 * </ol>
 * 为什么抽为纯函数：状态 × 归属的判定是审计口径，集中一处可单测穷举；散落 if 链一旦漏判即错发业务码。
 * 并发安全由 Mapper 的条件更新（{@code WHERE status = 1}）保证，本类只负责「更新失败后的归因」。
 */
public final class ParcelStatusMachine {

    private ParcelStatusMachine() {
    }

    /** 取件核销结局 */
    public enum PickupOutcome {
        /** 可核销 */
        SUCCESS,
        /** 包裹不存在（含跨站越权，归一为 7001） */
        NOT_EXISTS,
        /** 状态不允许取件（7002） */
        STATUS_INVALID,
        /** 已被他人取件（7003） */
        PICKED_BY_OTHER
    }

    /**
     * 依据当前状态与取件人判定结局。
     *
     * @param status          当前状态（可空）
     * @param pickupEmployeeId 记录中的取件人（状态 2 时可能有值）
     * @param operatorId       当前操作人
     */
    public static PickupOutcome decide(Integer status, Long pickupEmployeeId, Long operatorId) {
        if (status == null) {
            return PickupOutcome.STATUS_INVALID;
        }
        if (status == ParcelConstants.STATUS_IN_STOCK) {
            return PickupOutcome.SUCCESS;
        }
        if (status == ParcelConstants.STATUS_PICKED
                && pickupEmployeeId != null
                && !pickupEmployeeId.equals(operatorId)) {
            return PickupOutcome.PICKED_BY_OTHER;
        }
        return PickupOutcome.STATUS_INVALID;
    }
}
