package com.qiujie.service.sync;

import com.qiujie.common.PageResult;
import com.qiujie.dto.sync.SyncTaskQuery;
import com.qiujie.vo.sync.SyncTaskLogVO;
import com.qiujie.vo.sync.SyncTaskVO;

import java.util.List;

/**
 * 同步任务服务（M9，5 接口，Mock {@code routes/syncTask.js}，架构 §6.2 P9）。
 * <p>
 * 越权口径逐端点（不得统一）：详情 / 日志跨站 → 404；trigger / retry 跨站 → 403；列表静默收敛。
 */
public interface SyncTaskService {

    /** 任务分页（stationId 由 L1 静默收敛；status / keyword 可选） */
    PageResult<SyncTaskVO> page(SyncTaskQuery query);

    /** 任务详情（不存在 / 跨站 → 404） */
    SyncTaskVO detail(Long id);

    /** 批次日志（任务不存在 / 跨站 → 404） */
    List<SyncTaskLogVO> logs(Long id);

    /** 手动触发（跨站 → 403；非待领取 → 6001） */
    SyncTaskVO trigger(Long id);

    /** 失败重试（跨站 → 403；非失败 → 6001） */
    SyncTaskVO retry(Long id);
}
