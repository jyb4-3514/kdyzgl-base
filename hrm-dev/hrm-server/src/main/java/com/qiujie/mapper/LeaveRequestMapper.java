package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.LeaveRequest;

/**
 * 请假申请单 Mapper（db.md §7.1）。筛选条件（员工/驿站/状态/日期区间）由 Service 构造，禁止 SELECT *。
 */
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
}
