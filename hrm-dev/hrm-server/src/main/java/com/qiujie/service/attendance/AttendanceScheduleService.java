package com.qiujie.service.attendance;

import com.qiujie.dto.attendance.ScheduleBatchByStationRequest;
import com.qiujie.dto.attendance.ScheduleBatchRequest;
import com.qiujie.vo.attendance.MyScheduleVO;
import com.qiujie.vo.attendance.ScheduleMatrixVO;
import com.qiujie.vo.attendance.ScheduleSaveResultVO;
import com.qiujie.vo.attendance.ScheduleStationResultVO;

/**
 * 排班服务（4 接口：GET schedules / GET schedules/my / POST schedules/batch / POST schedules/batch-by-station）。
 * <p>
 * 唯一性 = {@code employeeId + workDate}（活跃唯一，Service 查重，不依赖数据库唯一索引）；
 * 整站排班在 {@code shiftId} 缺省时由 <b>S3 算法</b>（贪心构造 + 模拟退火）生成，失败降级返回贪心解 + 违规清单。
 */
public interface AttendanceScheduleService {

    /** 周排班矩阵（stationId 必填；weekStart 可空 = 本周） */
    ScheduleMatrixVO matrix(Long stationId, String weekStart);

    /** 我的排班（按周） */
    MyScheduleVO mine(Long employeeId, String weekStart);

    /** 手动批量保存（唯一性 employeeId+workDate，≤200 条） */
    ScheduleSaveResultVO saveBatch(ScheduleBatchRequest request);

    /** 整站排班：shiftId 给定 = 铺同一班次（Mock 语义）；缺省 = S3 智能排班 */
    ScheduleStationResultVO saveByStation(ScheduleBatchByStationRequest request);
}
