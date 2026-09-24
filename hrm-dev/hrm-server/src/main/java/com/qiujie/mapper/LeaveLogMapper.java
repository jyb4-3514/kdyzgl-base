package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.LeaveLog;

/**
 * 请假操作留痕 Mapper（db.md §7.2）。追加型：仅插入与按 leaveId 查询，不提供更新 / 删除。
 */
public interface LeaveLogMapper extends BaseMapper<LeaveLog> {
}
