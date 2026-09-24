package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.LeaveSetting;

/**
 * 请假全局设置 Mapper（db.md §8.7.1）。单行表：Service 取首行，无行时按默认值处理。
 */
public interface LeaveSettingMapper extends BaseMapper<LeaveSetting> {
}
