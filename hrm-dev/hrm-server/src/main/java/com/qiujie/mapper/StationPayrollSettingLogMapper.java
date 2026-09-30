package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.StationPayrollSettingLog;

/**
 * 驿站算薪配置变更审计 Mapper（DDL: V21 §3.6）。追加型：仅插入与按 stationId 查询，不提供更新 / 删除。
 */
public interface StationPayrollSettingLogMapper extends BaseMapper<StationPayrollSettingLog> {
}
