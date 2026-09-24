package com.qiujie.service.workorder;

import com.qiujie.common.PageResult;
import com.qiujie.dto.workorder.AutoDispatchRequest;
import com.qiujie.dto.workorder.DispatchRuleUpdateRequest;
import com.qiujie.dto.workorder.WorkOrderAssignRequest;
import com.qiujie.dto.workorder.WorkOrderCreateRequest;
import com.qiujie.dto.workorder.WorkOrderQuery;
import com.qiujie.dto.workorder.WorkOrderStatusRequest;
import com.qiujie.dto.workorder.WorkOrderTransferRequest;
import com.qiujie.vo.workorder.DispatchRuleVO;
import com.qiujie.vo.workorder.WorkOrderCreateVO;
import com.qiujie.vo.workorder.WorkOrderDetailVO;
import com.qiujie.vo.workorder.WorkOrderVO;

import java.util.List;

/**
 * 工单服务（M8 workorder，9 接口，api.md / Mock {@code routes/workOrder.js}，架构 §6.2 P8）。
 * <p>
 * 越权口径<b>逐端点</b>（架构 §6.2 P8，不得统一）：{@code {id}} 跨站 → 404；{@code assign}/{@code status}
 * → 8002；{@code transfer} → 8003。S6 多目标派单作为处理人推导内核（{@code auto-dispatch} 与
 * {@code assign} 默认处理人）。
 */
public interface WorkOrderService {

    /** 工单分页（超时筛选：overdueUnhandled 优先，overSla 为别名） */
    PageResult<WorkOrderVO> page(WorkOrderQuery query);

    /** 手工新建工单（source=MANUAL；可选即时指派） */
    WorkOrderCreateVO create(WorkOrderCreateRequest request);

    /** 自动派单规则列表（仅 ADMIN） */
    List<DispatchRuleVO> listDispatchRules();

    /** 维护自动派单规则（仅 ADMIN；规则不存在 → 8005） */
    DispatchRuleVO updateDispatchRule(Long id, DispatchRuleUpdateRequest request);

    /** 企微群消息自动派单（公开端点；内容为空 → 8006） */
    WorkOrderDetailVO autoDispatch(AutoDispatchRequest request);

    /** 工单详情（跨站 → 404） */
    WorkOrderDetailVO detail(Long id);

    /** 指派处理人（仅 ADMIN / 本站站长，越权 → 8002） */
    WorkOrderVO assign(Long id, WorkOrderAssignRequest request);

    /** 流转状态（状态机校验，非法 → 8001；越权 → 8002） */
    WorkOrderVO changeStatus(Long id, WorkOrderStatusRequest request);

    /** 转派处理人（不改状态；越权 → 8003；对象非法 → 8004） */
    WorkOrderDetailVO transfer(Long id, WorkOrderTransferRequest request);
}
