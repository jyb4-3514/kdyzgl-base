package com.qiujie.service.sync;

import com.qiujie.dto.sync.StationSyncConfigSaveRequest;
import com.qiujie.vo.sync.SyncOverviewVO;
import com.qiujie.vo.sync.SyncStationConfigVO;

import java.util.List;

/**
 * 驿站采集配置服务（M9，4 接口，Mock {@code routes/syncConfig.js}）。
 * <p>
 * 读（overview / configs / configs/{stationId}）对 ADMIN 与站长开放，非 ADMIN 静默收敛为本站；
 * 写（PUT configs/{stationId}）仅 ADMIN。{@code configs/{stationId}} 跨站 → 404。
 */
public interface StationSyncConfigService {

    /** 采集配置列表（非 ADMIN 收敛为本站） */
    List<SyncStationConfigVO> list();

    /** 单站配置（跨站 → 404；未配置 → 6002） */
    SyncStationConfigVO detail(Long stationId);

    /** 采集状态总览（四态计数 + 每站一行） */
    SyncOverviewVO overview();

    /** 保存采集配置（仅 ADMIN；驿站不存在 → 4001） */
    SyncStationConfigVO save(Long stationId, StationSyncConfigSaveRequest request);
}
