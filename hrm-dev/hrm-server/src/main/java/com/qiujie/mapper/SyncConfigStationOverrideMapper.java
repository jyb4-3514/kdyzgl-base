package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.SyncConfigStationOverride;

/**
 * 驿站覆盖值 Mapper（db.md §8.9.7）。按 (station_id, item_key) 取覆盖值 + 活跃唯一查重。
 */
public interface SyncConfigStationOverrideMapper extends BaseMapper<SyncConfigStationOverride> {
}
