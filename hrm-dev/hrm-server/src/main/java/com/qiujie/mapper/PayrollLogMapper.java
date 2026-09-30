package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.PayrollLog;

/**
 * 工资单操作留痕 Mapper（db.md §8.6.6）。追加型：仅插入与按 payrollId 查询，不提供更新 / 删除。
 */
public interface PayrollLogMapper extends BaseMapper<PayrollLog> {
}
