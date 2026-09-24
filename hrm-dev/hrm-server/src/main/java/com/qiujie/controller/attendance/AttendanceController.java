package com.qiujie.controller.attendance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.attendance.AttendanceCheckInRequest;
import com.qiujie.dto.attendance.AttendanceDetailQuery;
import com.qiujie.dto.attendance.AttendanceRecordQuery;
import com.qiujie.dto.attendance.AttendanceRuleQuery;
import com.qiujie.dto.attendance.AttendanceRuleRequest;
import com.qiujie.dto.attendance.AttendanceSummaryQuery;
import com.qiujie.dto.attendance.MyAttendanceQuery;
import com.qiujie.service.attendance.AttendanceRecordService;
import com.qiujie.service.attendance.AttendanceRuleService;
import com.qiujie.service.attendance.support.CsvSupport;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.AttendanceDetailVO;
import com.qiujie.vo.attendance.AttendanceRecordVO;
import com.qiujie.vo.attendance.AttendanceRuleVO;
import com.qiujie.vo.attendance.AttendanceStatusVO;
import com.qiujie.vo.attendance.AttendanceSummaryVO;
import com.qiujie.vo.attendance.MyAttendanceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考勤接口（10 接口：规则 3 + 状态 + 记录 4 + 打卡）。
 * <p>
 * 角色门槛逐条对齐 Mock {@code routes/attendance.js}；查询类端点非 ADMIN 的 {@code stationId} 由 L1 静默收敛为本人驿站
 * （{@code StationScopeQuery/StationScopedQuery}），「本人」端点（{@code /my}、{@code /status}、{@code /check-in}）
 * 一律以登录身份收口、不接受前端传 employeeId。
 * <p>
 * 路由顺序：{@code /rule/list}、{@code /export}、{@code /summary}、{@code /detail}、{@code /my}、{@code /status}、
 * {@code /check-in} 均为字面量段，Spring Boot 3 {@code PathPatternParser} 按模式特异性天然优先，无需人为 {@code @Order}。
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceRuleService attendanceRuleService;
    private final AttendanceRecordService attendanceRecordService;

    // ==================== 规则 ====================

    /** 单条规则（任何登录角色，但数据范围收敛为本人驿站） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/rule")
    public Result<AttendanceRuleVO> getRule(AttendanceRuleQuery query) {
        return Result.ok(attendanceRuleService.getRule(query.getStationId()));
    }

    /** 规则列表（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/rule/list")
    public Result<List<AttendanceRuleVO>> ruleList() {
        return Result.ok(attendanceRuleService.listRules());
    }

    /** 保存规则（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/rule")
    public Result<AttendanceRuleVO> saveRule(@RequestBody AttendanceRuleRequest request) {
        return Result.ok(attendanceRuleService.saveRule(request));
    }

    // ==================== 状态 / 打卡 ====================

    /** 今日打卡状态（本人驿站） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/status")
    public Result<AttendanceStatusVO> status() {
        return Result.ok(attendanceRecordService.status());
    }

    /** 打卡（服务端判定） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/check-in")
    public Result<AttendanceRecordVO> checkIn(@RequestBody AttendanceCheckInRequest request) {
        return Result.ok(attendanceRecordService.checkIn(request));
    }

    // ==================== 记录 / 概况 / 明细 / 导出 ====================

    /** 打卡记录分页 */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping("/records")
    public Result<PageResult<AttendanceRecordVO>> records(@Valid AttendanceRecordQuery query) {
        return Result.ok(attendanceRecordService.records(query));
    }

    /** 考勤记录导出（CSV 全量，内存流不落盘） */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(AttendanceRecordQuery query) {
        AttendanceRecordService.CsvExport csv = attendanceRecordService.export(query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, CsvSupport.CSV_CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, CsvSupport.disposition(csv.filename()))
                .body(csv.content());
    }

    /** 打卡概况 */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping("/summary")
    public Result<AttendanceSummaryVO> summary(AttendanceSummaryQuery query) {
        return Result.ok(attendanceRecordService.summary(query));
    }

    /** 考勤明细 */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping("/detail")
    public Result<AttendanceDetailVO> detail(AttendanceDetailQuery query) {
        return Result.ok(attendanceRecordService.detail(query));
    }

    /** 我的打卡（按月 + 今日状态） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/my")
    public Result<MyAttendanceVO> my(MyAttendanceQuery query) {
        return Result.ok(attendanceRecordService.mine(UserContext.getUserId(), query.getMonth()));
    }
}
