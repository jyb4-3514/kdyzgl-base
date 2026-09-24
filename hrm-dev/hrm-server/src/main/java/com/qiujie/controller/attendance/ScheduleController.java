package com.qiujie.controller.attendance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.attendance.MyScheduleQuery;
import com.qiujie.dto.attendance.ScheduleBatchByStationRequest;
import com.qiujie.dto.attendance.ScheduleBatchRequest;
import com.qiujie.dto.attendance.ScheduleQuery;
import com.qiujie.service.attendance.AttendanceScheduleService;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.MyScheduleVO;
import com.qiujie.vo.attendance.ScheduleMatrixVO;
import com.qiujie.vo.attendance.ScheduleSaveResultVO;
import com.qiujie.vo.attendance.ScheduleStationResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排班接口（4 接口：GET schedules、GET schedules/my、POST schedules/batch、POST schedules/batch-by-station）。
 * <p>
 * 排班矩阵非 ADMIN 静默收敛到本人驿站；「我的排班」以登录身份收口。
 * 整站排班在 {@code shiftId} 缺省时走 S3 智能排班（贪心 + 模拟退火），失败降级返回贪心解 + 违规清单。
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final AttendanceScheduleService attendanceScheduleService;

    /** 周排班矩阵 */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping
    public Result<ScheduleMatrixVO> matrix(ScheduleQuery query) {
        return Result.ok(attendanceScheduleService.matrix(query.getStationId(), query.getWeekStart()));
    }

    /** 我的排班（按周） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/my")
    public Result<MyScheduleVO> mine(MyScheduleQuery query) {
        return Result.ok(attendanceScheduleService.mine(UserContext.getUserId(), query.getWeekStart()));
    }

    /** 手动批量保存（仅 ADMIN，≤200 条） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/batch")
    public Result<ScheduleSaveResultVO> batch(@RequestBody ScheduleBatchRequest request) {
        return Result.ok(attendanceScheduleService.saveBatch(request));
    }

    /** 整站排班（仅 ADMIN）：shiftId 给定 = 铺同一班次；缺省 = S3 智能排班 */
    @RequireRoles({"ADMIN"})
    @PostMapping("/batch-by-station")
    public Result<ScheduleStationResultVO> batchByStation(@RequestBody ScheduleBatchByStationRequest request) {
        return Result.ok(attendanceScheduleService.saveByStation(request));
    }
}
