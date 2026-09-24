package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.SyncConfigOption;

/**
 * 选项集选项 Mapper（db.md §8.9.5）。按 (set_key, option_key) 查重/取选项。
 */
public interface SyncConfigOptionMapper extends BaseMapper<SyncConfigOption> {
}
