package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.PayrollRun;

/**
 * 自动算薪运行记录 Mapper（db.md §8.6.7）。
 * <p>
 * 认领（INSERT RUNNING）、终态回写、僵死回收 CAS 均由 Service 以条件更新（LambdaUpdateWrapper）表达，
 * 唯一键冲突由 InnoDB 原子拒绝（DuplicateKeyException），无需自定义 SQL。
 */
public interface PayrollRunMapper extends BaseMapper<PayrollRun> {
}
