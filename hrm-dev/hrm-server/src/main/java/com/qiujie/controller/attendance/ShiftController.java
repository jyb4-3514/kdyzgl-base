package com.qiujie.controller.attendance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.attendance.AttendanceShiftRequest;
import com.qiujie.dto.support.StationScopeQuery;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.vo.attendance.AttendanceShiftVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班次接口（4 接口：GET shifts、POST shifts、PUT shifts/{id}、DELETE shifts/{id}）。
 * <p>
 * 列表任何登录角色可读（非 ADMIN 静默收敛到本人驿站）；增删改仅 ADMIN。
 * 删除受保护：被排班引用时返回 400「该班次已被排班引用，不能删除」。
 */
@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final AttendanceShiftService attendanceShiftService;

    /** 按驿站列班次（按开始时间升序） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping
    public Result<List<AttendanceShiftVO>> list(StationScopeQuery query) {
        return Result.ok(attendanceShiftService.list(query.getStationId()));
    }

    /** 新增班次（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PostMapping
    public Result<AttendanceShiftVO> create(@RequestBody AttendanceShiftRequest request) {
        return Result.ok(attendanceShiftService.create(request));
    }

    /** 编辑班次（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/{id}")
    public Result<AttendanceShiftVO> update(@PathVariable Long id, @RequestBody AttendanceShiftRequest request) {
        return Result.ok(attendanceShiftService.update(id, request));
    }

    /** 删除班次（仅 ADMIN；被排班引用 → 400） */
    @RequireRoles({"ADMIN"})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attendanceShiftService.delete(id);
        return Result.ok();
    }
}
