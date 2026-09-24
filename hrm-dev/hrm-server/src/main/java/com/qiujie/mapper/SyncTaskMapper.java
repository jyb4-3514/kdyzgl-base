package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.SyncTask;

/**
 * 同步任务 Mapper（db.md §8.9.1）。过滤条件由 Service 构造（列表按 station_id/status + create_time 倒序）。
 */
public interface SyncTaskMapper extends BaseMapper<SyncTask> {
}
