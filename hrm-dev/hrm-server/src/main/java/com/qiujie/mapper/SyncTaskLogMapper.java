package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.SyncTaskLog;

/**
 * 同步任务日志 Mapper（db.md §8.9.2）。只增不改；按 (task_id, log_time) 取。
 */
public interface SyncTaskLogMapper extends BaseMapper<SyncTaskLog> {
}
