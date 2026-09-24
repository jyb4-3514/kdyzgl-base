package com.qiujie.vo.sync;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除影响面出参（配置项与选项同构；Mock {@code syncConfigStore.js#itemImpact / optionImpact}）。
 * <p>
 * {@code itemKey} 与 {@code optionKey} 按对象类型二选一有值。
 */
@Data
public class SyncImpactVO {

    private String itemKey;
    private String optionKey;
    /** 是否可删（内置项 / 被全局默认引用时为 false） */
    private boolean canDelete;
    /** 是否系统内置（内置只能停用，不可删除） */
    private boolean builtin;
    /** 受影响的驿站（含名称，供确认框展示） */
    private List<SyncImpactStationVO> referencedStations = new ArrayList<>();
    private int referencedCount;
    /** 阻断原因（空列表 = 当前可删） */
    private List<SyncImpactBlockerVO> blockers = new ArrayList<>();
}
