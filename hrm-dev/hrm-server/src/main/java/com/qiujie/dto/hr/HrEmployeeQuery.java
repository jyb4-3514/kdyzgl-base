package com.qiujie.dto.hr;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 按员工筛选的列表查询（档案列表 / 定薪列表共用，对齐 Mock {@code employeeScope}）：
 * {@code deptId} / {@code stationId} 精确过滤，{@code keyword} 匹配姓名或登录账号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HrEmployeeQuery extends PageQuery {

    /** 部门筛选（精确匹配，不做子部门展开：Mock 仅按 dept_id 相等过滤） */
    private Long deptId;

    /** 驿站筛选 */
    private Long stationId;

    /** 关键词：匹配姓名或登录账号 */
    private String keyword;
}
