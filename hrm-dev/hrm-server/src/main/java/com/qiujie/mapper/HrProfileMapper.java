package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.HrProfile;

/**
 * 人事档案 Mapper（db.md §8.5）。查重/过滤条件由 Service 构造（不建物化外键）。
 */
public interface HrProfileMapper extends BaseMapper<HrProfile> {
}
