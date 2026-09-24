package com.qiujie.controller.attendance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.attendance.AttendanceMakeupApproveRequest;
import com.qiujie.dto.attendance.AttendanceMakeupMineQuery;
import com.qiujie.dto.attendance.AttendanceMakeupQuery;
import com.qiujie.dto.attendance.AttendanceMakeupRequest;
import com.qiujie.service.attendance.AttendanceMakeupService;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.AttendanceMakeupVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 补卡接口（4 接口：makeup/my、makeup/list、POST makeup、makeup/{id}/approve）。
 * <p>
 * 提交与「我的」不限角色（员工本人）；列表与审批只有 ADMIN 可用。
 * 申请人只能是登录人本人，stationId 取登录人归属（不接收前端传参，防代他人申请）。
 */
@RestController
@RequestMapping("/api/v1/attendance/makeup")
@RequiredArgsConstructor
public class AttendanceMakeupController {

    private final AttendanceMakeupService attendanceMakeupService;

    /** 我的补卡（任何登录角色，数据以登录身份收口） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/my")
    public Result<PageResult<AttendanceMakeupVO>> mine(@Valid AttendanceMakeupMineQuery query) {
        return Result.ok(attendanceMakeupService.mine(UserContext.getUserId(), query));
    }

    /** 补卡列表（仅 ADMIN，stationId 缺省 = 全量） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/list")
    public Result<PageResult<AttendanceMakeupVO>> list(@Valid AttendanceMakeupQuery query) {
        return Result.ok(attendanceMakeupService.list(query));
    }

    /** 提交补卡（申请人 = 登录人本人） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping
    public Result<AttendanceMakeupVO> apply(@RequestBody AttendanceMakeupRequest request) {
        return Result.ok(attendanceMakeupService.apply(request));
    }

    /** 审批补卡（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/{id}/approve")
    public Result<AttendanceMakeupVO> approve(@PathVariable Long id,
                                             @RequestBody AttendanceMakeupApproveRequest request) {
        return Result.ok(attendanceMakeupService.approve(id, request));
    }
}
