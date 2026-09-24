package com.qiujie.service.attendance;

import com.qiujie.common.PageResult;
import com.qiujie.dto.attendance.AttendanceMakeupApproveRequest;
import com.qiujie.dto.attendance.AttendanceMakeupMineQuery;
import com.qiujie.dto.attendance.AttendanceMakeupQuery;
import com.qiujie.dto.attendance.AttendanceMakeupRequest;
import com.qiujie.vo.attendance.AttendanceMakeupVO;

/**
 * 补卡服务（4 接口：makeup/my / makeup/list / POST makeup / makeup/{id}/approve）。
 * <p>
 * 提交校验顺序固定为「规则 → 时段 → 重复申请 → 已有正常打卡」（与打卡共用「员工+日期+时段+类型」槽位口径）；
 * 审批仅 ADMIN，通过则补录打卡记录（{@code source=MAKEUP}、校验项置 null、打卡时间取时段规定时间）。
 */
public interface AttendanceMakeupService {

    /** 我的补卡（以登录身份收口） */
    PageResult<AttendanceMakeupVO> mine(Long employeeId, AttendanceMakeupMineQuery query);

    /** 补卡列表（仅 ADMIN，stationId 可空 = 全量） */
    PageResult<AttendanceMakeupVO> list(AttendanceMakeupQuery query);

    /** 提交补卡申请（申请人 = 登录人本人） */
    AttendanceMakeupVO apply(AttendanceMakeupRequest request);

    /** 审批补卡（仅 ADMIN；通过则补录打卡记录） */
    AttendanceMakeupVO approve(Long id, AttendanceMakeupApproveRequest request);
}
