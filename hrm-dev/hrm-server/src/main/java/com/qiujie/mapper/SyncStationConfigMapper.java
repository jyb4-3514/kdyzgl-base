package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.SyncStationConfig;

/**
 * 驿站采集配置 Mapper（db.md §8.9.3）。按 station_id 取配置（一驿一行）。
 */
public interface SyncStationConfigMapper extends BaseMapper<SyncStationConfig> {
}
