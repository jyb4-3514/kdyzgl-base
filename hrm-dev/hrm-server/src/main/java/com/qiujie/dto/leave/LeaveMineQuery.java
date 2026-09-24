package com.qiujie.dto.leave;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的请假查询（api.md §7.1 #3）：数据以登录身份收口，不接受 employeeId。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveMineQuery extends PageQuery {

    /** 状态筛选；'PENDING' 为服务端展开的聚合虚拟值 */
    private String status;

    /** 假别筛选 */
    private String leaveType;

    /** 区间起（按「区间有交集」筛选，可空） */
    private String startDate;

    /** 区间止（可空） */
    private String endDate;
}
