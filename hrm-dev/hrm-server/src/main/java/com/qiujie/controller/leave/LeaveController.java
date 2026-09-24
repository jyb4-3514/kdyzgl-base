package com.qiujie.controller.leave;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.leave.LeaveApplyRequest;
import com.qiujie.dto.leave.LeaveApproveRequest;
import com.qiujie.dto.leave.LeaveMineQuery;
import com.qiujie.dto.leave.LeavePreviewRequest;
import com.qiujie.dto.leave.LeaveQuery;
import com.qiujie.dto.leave.LeaveRevokeRequest;
import com.qiujie.dto.leave.LeaveSettingRequest;
import com.qiujie.service.leave.LeaveService;
import com.qiujie.vo.leave.LeavePreviewVO;
import com.qiujie.vo.leave.LeaveSettingVO;
import com.qiujie.vo.leave.LeaveVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请假接口（M7，13 接口，api.md §7.1 / Mock {@code routes/leave.js}，架构 §6.2 P7）。
 * <p>
 * <b>路由字面量优先</b>：{@code /preview}、{@code /my}、{@code /list}、{@code /settings} 为字面量段，
 * Spring Boot 3 的 {@code PathPatternParser} 按模式特异性排序（字面量段 > 变量段），天然优先于 {@code /{id}}，
 * <b>无需人为 @Order</b>（结论见架构 §1.4.7；回归断言见 {@code LeaveRouteOrderTest}）。
 * <p>
 * <b>角色门槛</b>逐条对齐 Mock {@code roles}：列表限 ADMIN/STATION_ADMIN，扣款开关限 ADMIN，
 * 初审限 STATION_ADMIN，终审/撤回限 ADMIN；其余任何登录角色可达，但数据以登录身份/归属驿站收口。
 * <p>
 * <b>契约冲突登记</b>：{@code GET /leave/settings} 的 roles 在 api.md §7.1 记「不限角色」、Mock 限 ADMIN；
 * 按主智能体裁决以 <b>Mock（ADMIN）</b>为准，待 api.md 收口。
 */
@RestController
@RequestMapping("/api/v1/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /** 提交请假申请 */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping
    public Result<LeaveVO> apply(@RequestBody LeaveApplyRequest request) {
        return Result.ok(leaveService.apply(request));
    }

    /** 请假试算（只算不落库；字面量端点，优先于 /{id}） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/preview")
    public Result<LeavePreviewVO> preview(@RequestBody LeavePreviewRequest request) {
        return Result.ok(leaveService.preview(request));
    }

    /** 我的请假（字面量端点，数据以登录身份收口） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/my")
    public Result<PageResult<LeaveVO>> mine(@Valid LeaveMineQuery query) {
        return Result.ok(leaveService.mine(query));
    }

    /** 请假列表（ADMIN 全域 / STATION_ADMIN 本站；字面量端点优先于 /{id}） */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping("/list")
    public Result<PageResult<LeaveVO>> list(@Valid LeaveQuery query) {
        return Result.ok(leaveService.list(query));
    }

    /** 请假扣款开关读取（仅 ADMIN；字面量端点优先于 /{id}） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/settings")
    public Result<LeaveSettingVO> getSettings() {
        return Result.ok(leaveService.getSettings());
    }

    /** 请假扣款开关保存（仅 ADMIN） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/settings")
    public Result<LeaveSettingVO> saveSettings(@RequestBody LeaveSettingRequest request) {
        return Result.ok(leaveService.saveSettings(request));
    }

    /** 请假详情（可见范围：ADMIN 全量 / 站长本站 / 本人；越权 9605） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/{id}")
    public Result<LeaveVO> detail(@PathVariable Long id) {
        return Result.ok(leaveService.detail(id));
    }

    /** 编辑申请（仅申请本人且待初审） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/{id}")
    public Result<LeaveVO> update(@PathVariable Long id, @RequestBody LeaveApplyRequest request) {
        return Result.ok(leaveService.update(id, request));
    }

    /** 申请人撤销（两种待审态） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/{id}/cancel")
    public Result<LeaveVO> cancel(@PathVariable Long id) {
        return Result.ok(leaveService.cancel(id));
    }

    /** 修改重提（原单 REJECTED，生成新单带 originId） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/{id}/resubmit")
    public Result<LeaveVO> resubmit(@PathVariable Long id, @RequestBody LeaveApplyRequest request) {
        return Result.ok(leaveService.resubmit(id, request));
    }

    /** 站长初审（仅 STATION_ADMIN，限本站） */
    @RequireRoles({"STATION_ADMIN"})
    @PostMapping("/{id}/station-approve")
    public Result<LeaveVO> stationApprove(@PathVariable Long id, @RequestBody LeaveApproveRequest request) {
        return Result.ok(leaveService.stationApprove(id, request));
    }

    /** 老板终审（仅 ADMIN，通过时落计薪天数快照） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/{id}/final-approve")
    public Result<LeaveVO> finalApprove(@PathVariable Long id, @RequestBody LeaveApproveRequest request) {
        return Result.ok(leaveService.finalApprove(id, request));
    }

    /** 撤回已批单（仅 ADMIN + 账期锁校验 9606） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/{id}/revoke")
    public Result<LeaveVO> revoke(@PathVariable Long id, @RequestBody LeaveRevokeRequest request) {
        return Result.ok(leaveService.revoke(id, request));
    }
}
