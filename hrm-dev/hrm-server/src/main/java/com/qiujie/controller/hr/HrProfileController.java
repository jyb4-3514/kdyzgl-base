package com.qiujie.controller.hr;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.hr.HrEmployeeQuery;
import com.qiujie.dto.hr.HrProfileUpdateRequest;
import com.qiujie.dto.hr.HrSalaryUpdateRequest;
import com.qiujie.service.hr.HrProfileService;
import com.qiujie.vo.hr.HrProfileDetailVO;
import com.qiujie.vo.hr.HrProfileVO;
import com.qiujie.vo.hr.HrSalaryDetailVO;
import com.qiujie.vo.hr.HrSalaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人事档案与定薪接口（6 接口，api.md / Mock {@code routes/hr.js}，架构 §6.2 P5）。
 * <p>
 * 角色门槛：列表与写操作仅 ADMIN；详情端点任意登录角色可达，但越权（非本人非 ADMIN）→ 403
 * （策略在 Service 内按端点口径判定，见架构 ADR-07：403/404 分界必须逐端点声明）。
 */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrProfileController {

    private final HrProfileService hrProfileService;

    /** 人事档案列表（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/profiles")
    public Result<PageResult<HrProfileVO>> listProfiles(@Valid HrEmployeeQuery query) {
        return Result.ok(hrProfileService.listProfiles(query));
    }

    /** 人事档案详情（任意登录角色；非本人非 ADMIN → 403） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/profiles/{employeeId}")
    public Result<HrProfileDetailVO> profileDetail(@PathVariable Long employeeId) {
        return Result.ok(hrProfileService.profileDetail(employeeId));
    }

    /** 编辑人事档案（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/profiles/{employeeId}")
    public Result<HrProfileVO> updateProfile(@PathVariable Long employeeId,
                                             @RequestBody(required = false) HrProfileUpdateRequest request) {
        return Result.ok(hrProfileService.updateProfile(employeeId, request));
    }

    /** 定薪列表（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/salary-structures")
    public Result<PageResult<HrSalaryVO>> listSalaries(@Valid HrEmployeeQuery query) {
        return Result.ok(hrProfileService.listSalaries(query));
    }

    /** 定薪详情（任意登录角色；非本人非 ADMIN → 403） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/salary-structures/{employeeId}")
    public Result<HrSalaryDetailVO> salaryDetail(@PathVariable Long employeeId) {
        return Result.ok(hrProfileService.salaryDetail(employeeId));
    }

    /** 保存定薪（仅 ADMIN）：覆盖当前档案 + 追加调薪留痕 */
    @RequireRoles({"ADMIN"})
    @PutMapping("/salary-structures/{employeeId}")
    public Result<HrSalaryDetailVO> updateSalary(@PathVariable Long employeeId,
                                                 @RequestBody(required = false) HrSalaryUpdateRequest request) {
        return Result.ok(hrProfileService.updateSalary(employeeId, request));
    }
}
