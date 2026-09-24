package com.qiujie.controller.workorder;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.workorder.AutoDispatchRequest;
import com.qiujie.dto.workorder.DispatchRuleUpdateRequest;
import com.qiujie.dto.workorder.WorkOrderAssignRequest;
import com.qiujie.dto.workorder.WorkOrderCreateRequest;
import com.qiujie.dto.workorder.WorkOrderQuery;
import com.qiujie.dto.workorder.WorkOrderStatusRequest;
import com.qiujie.dto.workorder.WorkOrderTransferRequest;
import com.qiujie.service.workorder.WorkOrderService;
import com.qiujie.vo.workorder.DispatchRuleVO;
import com.qiujie.vo.workorder.WorkOrderCreateVO;
import com.qiujie.vo.workorder.WorkOrderDetailVO;
import com.qiujie.vo.workorder.WorkOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单接口（M8，9 接口，api.md / Mock {@code routes/workOrder.js}，架构 §6.2 P8）。
 * <p>
 * <b>路由字面量优先</b>：{@code /dispatch-rules}、{@code /auto-dispatch} 为字面量段，
 * Spring Boot 3 的 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），天然优先于 {@code /{id}}，
 * <b>无需人为 @Order</b>（架构 §1.4.7；回归断言见 {@code WorkOrderRouteOrderTest}）。
 * <p>
 * <b>角色门槛</b>逐条对齐 Mock {@code roles}：{@code dispatch-rules} 读/写限 ADMIN；其余任何登录角色可达，
 * 数据以「归属驿站 + 处理人」在 Service 内收口。
 * <p>
 * <b>越权口径逐端点</b>（架构 §6.2 P8，不得统一）：{@code GET /{id}} 跨站 → 404；
 * {@code PUT /{id}/assign}、{@code PUT /{id}/status} → 业务码 8002；{@code POST /{id}/transfer} → 业务码 8003。
 */
@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    /** 工单分页（超时筛选 overdueUnhandled / overSla） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping
    public Result<PageResult<WorkOrderVO>> list(@Valid WorkOrderQuery query) {
        return Result.ok(workOrderService.page(query));
    }

    /** 手工新建工单 */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping
    public Result<WorkOrderCreateVO> create(@RequestBody WorkOrderCreateRequest request) {
        return Result.ok(workOrderService.create(request));
    }

    /** 自动派单规则列表（仅 ADMIN；字面量端点优先于 /{id}） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/dispatch-rules")
    public Result<List<DispatchRuleVO>> dispatchRules() {
        return Result.ok(workOrderService.listDispatchRules());
    }

    /** 维护自动派单规则（仅 ADMIN；规则不存在 → 8005） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/dispatch-rules/{id}")
    public Result<DispatchRuleVO> updateDispatchRule(@PathVariable Long id,
                                                     @RequestBody DispatchRuleUpdateRequest request) {
        return Result.ok(workOrderService.updateDispatchRule(id, request));
    }

    /**
     * 企微群消息自动派单（公开端点，Mock {@code auth:false}；字面量端点优先于 /{id}）。
     * <p>
     * <b>安全提示</b>：真实企微回调携带签名而非本系统 JWT，本接口当前只接受已解析明文
     * {@code {groupName, senderName, content, sendTime, stationId}}。
     * TODO(扩展): 接入企业微信机器人回调时替换为真实签名校验与消息解密（校验 {@code msg_signature}/{@code timestamp}/{@code nonce}，
     *   用 {@code EncodingAESKey} 解密 {@code Encrypt}）。
     * <b>公网可达须先经网络安全工程师评估（P0.5）</b>：签名/解密落地前不得直接公网暴露（建议先内网/白名单），
     * 部署时不得因白名单已就位就放行（架构 8-3 / R-3）。
     */
    @PostMapping("/auto-dispatch")
    public Result<WorkOrderDetailVO> autoDispatch(@RequestBody AutoDispatchRequest request) {
        return Result.ok(workOrderService.autoDispatch(request));
    }

    /** 工单详情（跨站 → 404） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/{id}")
    public Result<WorkOrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(workOrderService.detail(id));
    }

    /** 指派处理人（仅 ADMIN / 本站站长；越权 → 8002） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/{id}/assign")
    public Result<WorkOrderVO> assign(@PathVariable Long id, @RequestBody WorkOrderAssignRequest request) {
        return Result.ok(workOrderService.assign(id, request));
    }

    /** 流转状态（非法流转 → 8001；越权 → 8002） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PutMapping("/{id}/status")
    public Result<WorkOrderVO> changeStatus(@PathVariable Long id, @RequestBody WorkOrderStatusRequest request) {
        return Result.ok(workOrderService.changeStatus(id, request));
    }

    /** 转派处理人（不改状态；越权 → 8003；对象非法 → 8004） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/{id}/transfer")
    public Result<WorkOrderDetailVO> transfer(@PathVariable Long id, @RequestBody WorkOrderTransferRequest request) {
        return Result.ok(workOrderService.transfer(id, request));
    }
}
