package com.qiujie.service.attendance;

import com.qiujie.common.PageResult;
import com.qiujie.dto.attendance.AttendanceCheckInRequest;
import com.qiujie.dto.attendance.AttendanceDetailQuery;
import com.qiujie.dto.attendance.AttendanceRecordQuery;
import com.qiujie.dto.attendance.AttendanceSummaryQuery;
import com.qiujie.vo.attendance.AttendanceDetailVO;
import com.qiujie.vo.attendance.AttendanceRecordVO;
import com.qiujie.vo.attendance.AttendanceStatusVO;
import com.qiujie.vo.attendance.AttendanceSummaryVO;
import com.qiujie.vo.attendance.MyAttendanceVO;

import java.time.LocalDate;

/**
 * 打卡记录服务（7 接口：records / export / summary / detail / my / status / check-in）。
 * <p>
 * 出勤口径（应到=排班人数、实到=非 ABNORMAL 上班卡）与打卡判定链是本域核心，实现见
 * {@code support/AttendanceSummaryPolicy} 与 {@code support/AttendanceCheckPolicy}（纯逻辑，可离线单测）。
 */
public interface AttendanceRecordService {

    /** 打卡记录分页 */
    PageResult<AttendanceRecordVO> records(AttendanceRecordQuery query);

    /** 考勤记录导出（CSV 全量，与 records 同一筛选口径） */
    CsvExport export(AttendanceRecordQuery query);

    /** 打卡概况 */
    AttendanceSummaryVO summary(AttendanceSummaryQuery query);

    /** 考勤明细（按维度） */
    AttendanceDetailVO detail(AttendanceDetailQuery query);

    /** 我的打卡（按月 + 今日状态） */
    MyAttendanceVO mine(Long employeeId, String month);

    /** 今日打卡状态（本人驿站） */
    AttendanceStatusVO status();

    /** 打卡（服务端判定，前端无法绕过） */
    AttendanceRecordVO checkIn(AttendanceCheckInRequest request);

    /**
     * 是否存在有效卡（非 ABNORMAL）占用该槽位（员工 + 日期 + 类型 + 时段；periodIndex 为 null 表示不限定时段）。
     * 供补卡申请/审批复用「槽位去重」口径，避免两处各写一份判定。
     */
    boolean hasValidCard(Long employeeId, LocalDate workDate, String checkType, Integer periodIndex);

    /** CSV 导出结果（内存流，不落盘） */
    record CsvExport(byte[] content, String filename) {
    }
}
