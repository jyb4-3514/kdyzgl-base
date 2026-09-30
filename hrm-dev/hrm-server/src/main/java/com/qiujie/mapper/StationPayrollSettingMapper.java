package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.StationPayrollSetting;

/**
 * 驿站算薪配置 Mapper（db.md §8.6.5）。一驿一条；活跃唯一由 Service 查重保证。
 */
public interface StationPayrollSettingMapper extends BaseMapper<StationPayrollSetting> {
}
