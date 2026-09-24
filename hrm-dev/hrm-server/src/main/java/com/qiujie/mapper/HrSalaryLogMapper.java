package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.HrSalaryLog;

/**
 * 调薪留痕 Mapper（db.md §8.5）。追加型审计表：只 insert / select，不 update / delete。
 */
public interface HrSalaryLogMapper extends BaseMapper<HrSalaryLog> {
}
