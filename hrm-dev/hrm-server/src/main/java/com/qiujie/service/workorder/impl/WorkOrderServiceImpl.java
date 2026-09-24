package com.qiujie.service.workorder.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.LoginUser;
import com.qiujie.common.PageResult;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.workorder.AutoDispatchRequest;
import com.qiujie.dto.workorder.DispatchRuleUpdateRequest;
import com.qiujie.dto.workorder.WorkOrderAssignRequest;
import com.qiujie.dto.workorder.WorkOrderCreateRequest;
import com.qiujie.dto.workorder.WorkOrderQuery;
import com.qiujie.dto.workorder.WorkOrderStatusRequest;
import com.qiujie.dto.workorder.WorkOrderTransferRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.entity.WorkOrder;
import com.qiujie.entity.WorkOrderDispatchRule;
import com.qiujie.entity.WorkOrderTimeline;
import com.qiujie.entity.WorkOrderTransfer;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.WorkOrderDispatchRuleMapper;
import com.qiujie.mapper.WorkOrderMapper;
import com.qiujie.mapper.WorkOrderTimelineMapper;
import com.qiujie.mapper.WorkOrderTransferMapper;
import com.qiujie.service.notification.NotificationService;
import com.qiujie.service.workorder.WorkOrderService;
import com.qiujie.service.workorder.support.WorkOrderAccessPolicy;
import com.qiujie.service.workorder.support.WorkOrderConstants;
import com.qiujie.service.workorder.support.WorkOrderDispatchScorer;
import com.qiujie.service.workorder.support.WorkOrderKeywordClassifier;
import com.qiujie.service.workorder.support.WorkOrderSlaPolicy;
import com.qiujie.service.workorder.support.WorkOrderStateMachine;
import com.qiujie.util.UserContext;
import com.qiujie.vo.workorder.DispatchRuleVO;
import com.qiujie.vo.workorder.HandleLogVO;
import com.qiujie.vo.workorder.WorkOrderCreateVO;
import com.qiujie.vo.workorder.WorkOrderDetailVO;
import com.qiujie.vo.workorder.WorkOrderTransferVO;
import com.qiujie.vo.workorder.WorkOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 工单服务实现（M8，9 接口，api.md / Mock {@code routes/workOrder.js}，架构 §6.2 P8）。
 * <p>
 * 关键口径：
 * <ul>
 *   <li><b>状态机</b>：{@link WorkOrderStateMachine}（0→1/3，1→2，2→1/3，3→终态）；非法流转 8001；</li>
 *   <li><b>SLA</b>：{@link WorkOrderSlaPolicy}，阈值外置 {@code hrm.algo.dispatch.slaHours}（Q4 未裁定前 48/24/8 等价现状）；
 *       「超时未处理」仅未处理完（0/1）且 now &gt; deadline，出参同时给 {@code overdueUnhandled} 与旧别名 {@code overSla}；</li>
 *   <li><b>S6 多目标派单</b>：{@link WorkOrderDispatchScorer} 作排序内核，用于 {@code auto-dispatch} 与
 *       {@code assign} 的默认处理人推导；无候选 / 权重非法时降级为不指派（转人工），关键词规则与兜底类型优先级不受影响；</li>
 *   <li><b>越权</b>：逐端点 —— {@code {id}} 跨站 404、{@code assign}/{@code status} 8002、{@code transfer} 8003；</li>
 *   <li><b>留痕</b>：{@code work_order_timeline} 组装回 {@code handleLog[]}（同序）；转单另落 {@code work_order_transfer}；</li>
 *   <li><b>通知</b>：指派/转单（type 1）、解决（type 2）走 P2 {@code NotificationService.sendSystem}。</li>
 * </ul>
 * <b>单号生成</b>：Mock 用内存全局序号拼 {@code WO-yyyyMMdd-####}；服务端以「插入取得自增 id 后回填单号」等价实现
 * （order_no 无唯一索引，占位值安全；两次写在同一事务内）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** ADMIN 新建工单未指定驿站时的缺省值（对齐 Mock {@code isBlank(body.stationId) ? 1 : ...}） */
    private static final long DEFAULT_STATION_ID = 1L;

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderTimelineMapper timelineMapper;
    private final WorkOrderTransferMapper transferMapper;
    private final WorkOrderDispatchRuleMapper dispatchRuleMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final NotificationService notificationService;
    private final AlgoProperties algoProperties;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<WorkOrderVO> page(WorkOrderQuery query) {
        WorkOrderQuery q = query == null ? new WorkOrderQuery() : query;
        int pageNum = q.getPageNum() == null ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null ? 10 : q.getPageSize();

        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        // stationId 已由 L1 数据范围收敛（非 ADMIN 强制本人驿站；无归属收敛为哨兵 -1 → 空结果）
        wrapper.eq(q.getStationId() != null, WorkOrder::getStationId, q.getStationId())
                .eq(q.getStatus() != null, WorkOrder::getStatus, q.getStatus())
                .eq(q.getType() != null, WorkOrder::getType, q.getType())
                .eq(q.getPriority() != null, WorkOrder::getPriority, q.getPriority())
                .eq(q.getAssigneeId() != null, WorkOrder::getAssigneeId, q.getAssigneeId());
        String keyword = trimToNull(q.getKeyword());
        if (keyword != null) {
            // 关键词匹配工单号或标题（对齐 Mock includes 语义 → LIKE %kw%）
            wrapper.and(w -> w.like(WorkOrder::getOrderNo, keyword).or().like(WorkOrder::getTitle, keyword));
        }
        if (overdueOnly(q)) {
            // 超时筛选在 DB 层完成（等价于 Mock 先过滤再分页）：仅未处理完且已过 SLA 截止
            wrapper.in(WorkOrder::getStatus, WorkOrderConstants.OPEN_STATUSES)
                    .lt(WorkOrder::getSlaDeadline, LocalDateTime.now());
        }
        wrapper.orderByDesc(WorkOrder::getCreateTime).orderByDesc(WorkOrder::getId);

        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords(), LocalDateTime.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderDetailVO detail(Long id) {
        WorkOrder order = findOrder(id);
        LoginUser user = currentUser();
        if (!WorkOrderAccessPolicy.canView(user.getRole(), parseStationId(user.getStationId()), order.getStationId())) {
            // 跨站按「不存在」返回，避免暴露他人驿站工单的存在性（对齐 Mock detail）
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return toDetail(order, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchRuleVO> listDispatchRules() {
        List<WorkOrderDispatchRule> rules = dispatchRuleMapper.selectList(
                new LambdaQueryWrapper<WorkOrderDispatchRule>().orderByAsc(WorkOrderDispatchRule::getId));
        List<DispatchRuleVO> list = new ArrayList<>(rules.size());
        for (WorkOrderDispatchRule rule : rules) {
            list.add(toRuleVO(rule));
        }
        return list;
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderCreateVO create(WorkOrderCreateRequest request) {
        LoginUser user = currentUser();
        WorkOrderCreateRequest req = request == null ? new WorkOrderCreateRequest() : request;
        if (req.getType() == null || !WorkOrderConstants.TYPES.contains(req.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单类型非法");
        }
        if (req.getPriority() == null || !WorkOrderConstants.PRIORITIES.contains(req.getPriority())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优先级非法");
        }
        if (!textLen(req.getTitle(), WorkOrderConstants.TITLE_MIN, WorkOrderConstants.TITLE_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题长度须为 1-100 字符");
        }
        // 描述兼容 content / description；缺省视为空串（Mock content || ''）
        String content = req.getContent() != null ? req.getContent() : req.getDescription();
        if (content != null && !textLen(content, 0, WorkOrderConstants.CONTENT_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "描述长度不可超过 500 字符");
        }
        Long stationId = resolveCreateStation(user, req.getStationId());
        Employee operator = loadEmployee(user.getUserId());
        Employee assignee = resolveCreateAssignee(req.getAssigneeId(), stationId, user.getRole());

        LocalDateTime now = LocalDateTime.now();
        WorkOrder order = new WorkOrder();
        order.setType(req.getType());
        order.setStatus(WorkOrderConstants.STATUS_PENDING);
        order.setPriority(req.getPriority());
        order.setTitle(req.getTitle().trim());
        order.setContent(content == null ? "" : content);
        order.setSource(WorkOrderConstants.SOURCE_MANUAL);
        order.setStationId(stationId);
        order.setParcelId(req.getParcelId());
        // 运单号兼容 relatedWaybillNo / waybillNo（Mock 两键同义）
        order.setWaybillNo(firstNonBlank(req.getRelatedWaybillNo(), req.getWaybillNo()));
        order.setReporterId(user.getUserId());
        order.setAssigneeId(assignee == null ? null : assignee.getId());
        order.setSlaDeadline(WorkOrderSlaPolicy.deadline(now, req.getPriority(), slaHours(), defaultSlaHours()));
        order.setOrderNo(placeholderOrderNo());
        workOrderMapper.insert(order);
        // 自增 id 取得后回填正式单号（Mock 用内存序号拼号）
        order.setOrderNo(buildOrderNo(order.getId(), now));
        workOrderMapper.updateById(order);

        pushTimeline(order.getId(), WorkOrderConstants.ACTION_CREATE, user.getUserId(), operator.getRealName(),
                "创建工单", now);
        if (assignee != null) {
            pushTimeline(order.getId(), WorkOrderConstants.ACTION_ASSIGN, user.getUserId(), operator.getRealName(),
                    "指派给 " + assignee.getRealName(), now);
            notifyAssign(order, assignee);
        }
        return new WorkOrderCreateVO(order.getId(), order.getOrderNo(), order.getSource());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchRuleVO updateDispatchRule(Long id, DispatchRuleUpdateRequest request) {
        WorkOrderDispatchRule rule = id == null ? null : dispatchRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.WORK_ORDER_DISPATCH_RULE_NOT_EXISTS);
        }
        DispatchRuleUpdateRequest req = request == null ? new DispatchRuleUpdateRequest() : request;
        if (req.getKeyword() != null && !textLen(req.getKeyword(), WorkOrderConstants.KEYWORD_MIN,
                WorkOrderConstants.KEYWORD_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关键词长度须为 1-20");
        }
        if (req.getWorkOrderType() != null && !WorkOrderConstants.TYPES.contains(req.getWorkOrderType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单类型非法");
        }
        if (req.getPriority() != null && !WorkOrderConstants.PRIORITIES.contains(req.getPriority())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优先级非法");
        }
        Integer enabled = normalizeEnabled(req.getEnabled());
        if (req.getDefaultAssigneeId() != null) {
            Employee employee = employeeMapper.selectById(req.getDefaultAssigneeId());
            if (employee == null || !isEnabled(employee)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "默认处理人不存在或已停用");
            }
            rule.setDefaultAssigneeId(employee.getId());
        }
        // 部分更新：仅在入参显式提供时改写
        if (req.getKeyword() != null) {
            rule.setKeyword(req.getKeyword().trim());
        }
        if (req.getWorkOrderType() != null) {
            rule.setWorkOrderType(req.getWorkOrderType());
        }
        if (req.getPriority() != null) {
            rule.setPriority(req.getPriority());
        }
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        rule.setUpdateTime(LocalDateTime.now());
        dispatchRuleMapper.updateById(rule);
        return toRuleVO(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderDetailVO autoDispatch(AutoDispatchRequest request) {
        AutoDispatchRequest req = request == null ? new AutoDispatchRequest() : request;
        String content = isBlank(req.getContent()) ? "" : req.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.WORK_ORDER_GROUP_MSG_INVALID);
        }
        Long stationId = req.getStationId() == null ? DEFAULT_STATION_ID : req.getStationId();
        if (stationMapper.selectById(stationId) == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        // 以消息发送时间作为建单基准：回调延迟到账时 SLA 倒计时仍按消息真实时间起算（对齐 Mock）
        LocalDateTime base = parseSendTime(req.getSendTime());

        WorkOrderKeywordClassifier.Result classified = classify(content);
        int type = classified.type();
        int priority = classified.priority();
        // 处理人推导：规则显式默认处理人优先；否则走 S6 多目标排序；无候选 → 不指派（转人工，不阻塞建单）
        Long assigneeId = classified.defaultAssigneeId();
        if (assigneeId == null) {
            assigneeId = deriveAssignee(stationId, type, priority, base);
        }

        String groupName = isBlank(req.getGroupName()) ? null : req.getGroupName().trim();
        String senderName = isBlank(req.getSenderName()) ? null : req.getSenderName().trim();
        String ruleKeyword = classified.matched() ? classified.keyword() : WorkOrderConstants.GROUP_MSG_LABEL;

        WorkOrder order = new WorkOrder();
        order.setType(type);
        order.setStatus(WorkOrderConstants.STATUS_PENDING);
        order.setPriority(priority);
        order.setTitle(ruleKeyword + "：" + titleFromContent(content));
        order.setContent(content);
        order.setSource(WorkOrderConstants.SOURCE_AUTO_WECHAT);
        order.setStationId(stationId);
        // 群消息发送人不在系统内，不伪造上报人（来源写入时间线）
        order.setReporterId(null);
        order.setAssigneeId(assigneeId);
        order.setSlaDeadline(WorkOrderSlaPolicy.deadline(base, priority, slaHours(), defaultSlaHours()));
        order.setOrderNo(placeholderOrderNo());
        // 建单基准 = 消息发送时间（Mock 让 create_time 亦等于 sendTime）
        order.setCreateTime(base);
        order.setUpdateTime(base);
        workOrderMapper.insert(order);
        // 回填单号 + 重设业务时间（防止自动填充覆盖为入库时间；UPDATE 显式写入 create_time）
        order.setOrderNo(buildOrderNo(order.getId(), base));
        order.setCreateTime(base);
        order.setUpdateTime(base);
        workOrderMapper.updateById(order);

        pushTimeline(order.getId(), WorkOrderConstants.ACTION_CREATE, null, WorkOrderConstants.AUTO_OPERATOR_NAME,
                "自动派发（命中规则：" + (classified.matched() ? classified.keyword() : "无，使用默认类型与优先级") + "）", base);
        pushTimeline(order.getId(), WorkOrderConstants.ACTION_AUTO_DISPATCH, null,
                WorkOrderConstants.AUTO_OPERATOR_NAME,
                "来源群：" + orDash(groupName) + "；发送人：" + orDash(senderName) + "；原始消息：" + content, base);

        if (assigneeId != null) {
            Employee assignee = employeeMapper.selectById(assigneeId);
            if (assignee != null) {
                notificationService.sendSystem(assignee.getId(), WorkOrderConstants.NOTIFY_ASSIGN, "工单指派",
                        "群消息自动派发工单 " + order.getOrderNo() + "：" + order.getTitle(),
                        WorkOrderConstants.BIZ_TYPE, order.getId());
            }
        }
        return toDetail(order, base);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO assign(Long id, WorkOrderAssignRequest request) {
        WorkOrder order = findOrder(id);
        LoginUser user = currentUser();
        if (!WorkOrderAccessPolicy.canAssign(user.getRole(), parseStationId(user.getStationId()), order.getStationId())) {
            throw new BusinessException(ErrorCode.WORK_ORDER_NO_PERMISSION);
        }
        Long targetId = request == null ? null : request.getAssigneeId();
        Long assigneeId;
        if (targetId != null) {
            Employee targetEmployee = employeeMapper.selectById(targetId);
            // 对齐 Mock：指派仅校验存在（不校验在职；在职约束只用于转单 8004 与建单指派）
            if (targetEmployee == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "被指派人不存在");
            }
            assigneeId = targetEmployee.getId();
        } else {
            // S6：未指定处理人时按多目标排序推导；无候选 → 退回 Mock 的「被指派人不存在」
            assigneeId = deriveAssignee(order.getStationId(), order.getType(), order.getPriority(), LocalDateTime.now());
            if (assigneeId == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "被指派人不存在");
            }
        }
        Employee assignee = loadEmployee(assigneeId);
        Employee operator = loadEmployee(user.getUserId());
        LocalDateTime now = LocalDateTime.now();
        order.setAssigneeId(assigneeId);
        order.setUpdateTime(now);
        workOrderMapper.updateById(order);

        pushTimeline(order.getId(), WorkOrderConstants.ACTION_ASSIGN, user.getUserId(), operator.getRealName(),
                "指派给 " + assignee.getRealName(), now);
        notifyAssign(order, assignee);
        return toVOList(List.of(order), now).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVO changeStatus(Long id, WorkOrderStatusRequest request) {
        WorkOrder order = findOrder(id);
        LoginUser user = currentUser();
        if (!WorkOrderAccessPolicy.canManage(user.getRole(), parseStationId(user.getStationId()), user.getUserId(),
                order.getStationId(), order.getAssigneeId())) {
            throw new BusinessException(ErrorCode.WORK_ORDER_NO_PERMISSION);
        }
        Integer target = request == null ? null : request.getStatus();
        // target 缺失或非法流转统一回 8001（对齐 Mock：includes(NaN) 为假 → 状态流转非法）
        if (target == null || !WorkOrderStateMachine.canTransition(order.getStatus(), target)) {
            throw new BusinessException(ErrorCode.WORK_ORDER_STATUS_INVALID);
        }
        Employee operator = loadEmployee(user.getUserId());
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(target);
        if (target == WorkOrderConstants.STATUS_RESOLVED) {
            order.setResolvedTime(now);
        }
        if (target == WorkOrderConstants.STATUS_CLOSED) {
            order.setClosedTime(now);
        }
        order.setUpdateTime(now);
        workOrderMapper.updateById(order);

        String remark = request == null || request.getRemark() == null ? "" : request.getRemark();
        pushTimeline(order.getId(), WorkOrderStateMachine.actionOf(target), user.getUserId(), operator.getRealName(),
                remark, now);
        if (target == WorkOrderConstants.STATUS_RESOLVED) {
            // 解决时通知上报人（Mock 二次联动；企微自动派发无上报人 → 不投递）
            if (order.getReporterId() != null) {
                notificationService.sendSystem(order.getReporterId(), WorkOrderConstants.NOTIFY_FLOW, "工单流转",
                        "您的工单 " + order.getOrderNo() + " 已解决", WorkOrderConstants.BIZ_TYPE, order.getId());
            }
        }
        return toVOList(List.of(order), now).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderDetailVO transfer(Long id, WorkOrderTransferRequest request) {
        WorkOrder order = findOrder(id);
        LoginUser user = currentUser();
        // 权限口径与流转一致（ADMIN / 本站站长 / 当前处理人），仅错误码区分（8003）
        if (!WorkOrderAccessPolicy.canManage(user.getRole(), parseStationId(user.getStationId()), user.getUserId(),
                order.getStationId(), order.getAssigneeId())) {
            throw new BusinessException(ErrorCode.WORK_ORDER_TRANSFER_NO_PERMISSION);
        }
        String reason = request == null ? null : request.getReason();
        if (!textLen(reason, WorkOrderConstants.REASON_MIN, WorkOrderConstants.REASON_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "转单理由长度须为 2-100 字");
        }
        Long toEmployeeId = request == null ? null : request.getToEmployeeId();
        Employee target = toEmployeeId == null ? null : employeeMapper.selectById(toEmployeeId);
        // 对象须在职，且不能转给自己（转给自己等于原地打转，留痕失去意义）
        if (target == null || !isEnabled(target) || target.getId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.WORK_ORDER_TRANSFER_TARGET_INVALID);
        }
        // 只有老板可跨站调人；站长与处理人转单只能在本站内消化
        if (!WorkOrderAccessPolicy.canTransferTo(user.getRole(), target.getStationId(), order.getStationId())) {
            throw new BusinessException(ErrorCode.WORK_ORDER_TRANSFER_TARGET_INVALID);
        }
        Employee operator = loadEmployee(user.getUserId());
        LocalDateTime now = LocalDateTime.now();
        String reasonText = reason.trim();
        Long fromId = order.getAssigneeId();
        order.setAssigneeId(target.getId());
        order.setUpdateTime(now);
        workOrderMapper.updateById(order);

        WorkOrderTransfer row = new WorkOrderTransfer();
        row.setWorkOrderId(order.getId());
        row.setFromEmployeeId(fromId);
        row.setFromEmployeeName(fromId == null ? null : realName(fromId));
        row.setToEmployeeId(target.getId());
        row.setToEmployeeName(target.getRealName());
        row.setReason(reasonText);
        row.setOperatorId(user.getUserId());
        row.setOperatorName(operator.getRealName());
        row.setTransferTime(now);
        transferMapper.insert(row);

        pushTimeline(order.getId(), WorkOrderConstants.ACTION_TRANSFER, user.getUserId(), operator.getRealName(),
                "转单给 " + target.getRealName() + "：" + reasonText, now);
        notificationService.sendSystem(target.getId(), WorkOrderConstants.NOTIFY_ASSIGN, "工单转单",
                "工单 " + order.getOrderNo() + " 已转由您处理：" + order.getTitle(),
                WorkOrderConstants.BIZ_TYPE, order.getId());
        return toDetail(order, now);
    }

    // ==================== 内部：S6 派单 ====================

    /** 关键词判定：默认顺序命中（与 Mock 等价），开关开启后走特异度加权 */
    private WorkOrderKeywordClassifier.Result classify(String content) {
        List<WorkOrderKeywordClassifier.Rule> rules = new ArrayList<>();
        for (WorkOrderDispatchRule rule : dispatchRuleMapper.selectList(
                new LambdaQueryWrapper<WorkOrderDispatchRule>()
                        .eq(WorkOrderDispatchRule::getEnabled, 1)
                        .orderByAsc(WorkOrderDispatchRule::getId))) {
            rules.add(new WorkOrderKeywordClassifier.Rule(rule.getId(), rule.getKeyword(),
                    rule.getWorkOrderType(), rule.getPriority(), rule.getDefaultAssigneeId()));
        }
        AlgoProperties.Dispatch dispatch = algoProperties.getDispatch();
        if (dispatch.isKeywordWeighted()) {
            return WorkOrderKeywordClassifier.classifyByScore(content, rules, dispatch.getKeywordWeights(),
                    dispatch.getDefaultType(), dispatch.getDefaultPriority());
        }
        return WorkOrderKeywordClassifier.classifyByOrder(content, rules,
                dispatch.getDefaultType(), dispatch.getDefaultPriority());
    }

    /**
     * S6 多目标排序推导处理人。
     * <p>
     * 三个信号由库中算出：技能画像（窗口内已了结工单的类型占比）、负载（窗口内接单量归一）、
     * 就绪度（未完成量 × 单件预估时长 → 相对 SLA 的时间窗）。
     * <b>失败降级</b>：无候选 / 权重非法 / 任何异常 → 返回 null（不指派、转人工），绝不阻塞建单。
     */
    private Long deriveAssignee(Long stationId, Integer type, Integer priority, LocalDateTime base) {
        if (stationId == null || type == null || priority == null) {
            return null;
        }
        try {
            List<Employee> candidates = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                    .select(Employee::getId)
                    .eq(Employee::getStationId, stationId)
                    .eq(Employee::getStatus, 1)
                    .orderByAsc(Employee::getId));
            if (candidates.isEmpty()) {
                return null;
            }
            List<Long> ids = candidates.stream().map(Employee::getId).toList();
            AlgoProperties.Dispatch dispatch = algoProperties.getDispatch();
            Map<Long, Integer> openLoad = countOpenByAssignee(ids);
            Map<Long, Integer> handled = countAssignedSince(ids, base.minusDays(Math.max(0, dispatch.getLoadWindowDays())));
            Map<Long, Double> skill = skillShareByAssignee(ids, type,
                    base.minusDays(Math.max(0, dispatch.getSkillWindowDays())), Math.max(0, dispatch.getMinSkillSamples()));

            List<WorkOrderDispatchScorer.Candidate> scored = new ArrayList<>(candidates.size());
            for (Employee employee : candidates) {
                scored.add(new WorkOrderDispatchScorer.Candidate(employee.getId(),
                        skill.getOrDefault(employee.getId(), 0.0),
                        handled.getOrDefault(employee.getId(), 0),
                        openLoad.getOrDefault(employee.getId(), 0)));
            }
            List<WorkOrderDispatchScorer.Score> ranked = WorkOrderDispatchScorer.rank(priority, scored, weights(),
                    slaHours(), defaultSlaHours(), dispatch.getEstimatedServiceHours());
            return WorkOrderDispatchScorer.select(ranked);
        } catch (RuntimeException e) {
            log.warn("S6 多目标派单不可用，降级为不指派（转人工）：stationId={}, type={}, priority={}",
                    stationId, type, priority, e);
            return null;
        }
    }

    /** 未完成量（状态 0/1）按处理人聚合（就绪度等待代理的输入） */
    private Map<Long, Integer> countOpenByAssignee(Collection<Long> assigneeIds) {
        return aggregate(
                workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                        .select(WorkOrder::getAssigneeId)
                        .in(WorkOrder::getAssigneeId, assigneeIds)
                        .in(WorkOrder::getStatus, WorkOrderConstants.OPEN_STATUSES)));
    }

    /** 负载窗口内接单量按处理人聚合（负载均衡项的输入） */
    private Map<Long, Integer> countAssignedSince(Collection<Long> assigneeIds, LocalDateTime since) {
        return aggregate(
                workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                        .select(WorkOrder::getAssigneeId)
                        .in(WorkOrder::getAssigneeId, assigneeIds)
                        .ge(WorkOrder::getCreateTime, since)));
    }

    /** 技能画像：窗口内已了结工单中该类型占比（样本不足按 0，不臆测无历史员工的特长） */
    private Map<Long, Double> skillShareByAssignee(Collection<Long> assigneeIds, int type, LocalDateTime since,
                                                   int minSamples) {
        List<WorkOrder> rows = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .select(WorkOrder::getAssigneeId, WorkOrder::getType)
                .in(WorkOrder::getAssigneeId, assigneeIds)
                .in(WorkOrder::getStatus, WorkOrderConstants.TERMINAL_STATUSES)
                .ge(WorkOrder::getCreateTime, since));
        Map<Long, int[]> counts = new HashMap<>();
        for (WorkOrder row : rows) {
            int[] pair = counts.computeIfAbsent(row.getAssigneeId(), key -> new int[2]);
            pair[0]++;
            if (row.getType() != null && row.getType() == type) {
                pair[1]++;
            }
        }
        Map<Long, Double> share = new HashMap<>(counts.size());
        counts.forEach((employeeId, pair) -> {
            if (pair[0] >= Math.max(1, minSamples) && pair[0] > 0) {
                share.put(employeeId, (double) pair[1] / pair[0]);
            }
        });
        return share;
    }

    private Map<Long, Integer> aggregate(List<WorkOrder> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (WorkOrder row : rows) {
            if (row.getAssigneeId() != null) {
                map.merge(row.getAssigneeId(), 1, Integer::sum);
            }
        }
        return map;
    }

    private WorkOrderDispatchScorer.Weights weights() {
        AlgoProperties.DispatchWeights w = algoProperties.getDispatch().getWeights();
        return new WorkOrderDispatchScorer.Weights(w.getUrgency(), w.getSkill(), w.getLoad(), w.getSpeed());
    }

    private Map<Integer, Integer> slaHours() {
        return algoProperties.getDispatch().getSlaHours();
    }

    private int defaultSlaHours() {
        return algoProperties.getDispatch().getDefaultSlaHours();
    }

    // ==================== 内部：校验与取值 ====================

    private Long resolveCreateStation(LoginUser user, Long bodyStationId) {
        if (RoleEnum.isAdmin(user.getRole())) {
            // Mock：ADMIN 缺省驿站为 1，且不校验存在性
            return bodyStationId == null ? DEFAULT_STATION_ID : bodyStationId;
        }
        Long stationId = parseStationId(user.getStationId());
        if (stationId == null) {
            // Mock 的登录用户恒有驿站；服务端对无归属账号显式拒绝，避免落库 station_id 为空
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号未归属驿站，无法新建工单");
        }
        return stationId;
    }

    /** 建单即时指派校验（对齐 Mock create）：须在职；非 ADMIN 只能指派本站在职员工（跨站回 8004） */
    private Employee resolveCreateAssignee(Long assigneeId, Long stationId, String role) {
        if (assigneeId == null) {
            return null;
        }
        Employee assignee = employeeMapper.selectById(assigneeId);
        if (assignee == null || !isEnabled(assignee)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "被指派人不存在或已停用");
        }
        if (!RoleEnum.isAdmin(role) && !stationId.equals(assignee.getStationId())) {
            throw new BusinessException(ErrorCode.WORK_ORDER_TRANSFER_TARGET_INVALID);
        }
        return assignee;
    }

    /** enabled 归一化：0/1 或 true/false（含字符串）；null → null（不改）；其余 → 400 */
    private Integer normalizeEnabled(Object enabled) {
        if (enabled == null) {
            return null;
        }
        if (enabled instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (enabled instanceof Number number) {
            int value = number.intValue();
            if (value == 0 || value == 1) {
                return value;
            }
        }
        if (enabled instanceof String text) {
            if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
                return 0;
            }
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                return 1;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "enabled 仅支持 0 / 1");
    }

    // ==================== 内部：出参组装 ====================

    private List<WorkOrderVO> toVOList(List<WorkOrder> rows, LocalDateTime now) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = new LinkedHashSet<>();
        Set<Long> stationIds = new LinkedHashSet<>();
        Set<Long> orderIds = new LinkedHashSet<>();
        for (WorkOrder row : rows) {
            addIfNotNull(employeeIds, row.getReporterId());
            addIfNotNull(employeeIds, row.getAssigneeId());
            addIfNotNull(stationIds, row.getStationId());
            if (row.getId() != null) {
                orderIds.add(row.getId());
            }
        }
        Map<Long, String> employeeNames = employeeNames(employeeIds);
        Map<Long, String> stationNames = stationNames(stationIds);
        Map<Long, List<WorkOrderTimeline>> logs = timelineByOrder(orderIds);

        List<WorkOrderVO> list = new ArrayList<>(rows.size());
        for (WorkOrder row : rows) {
            WorkOrderVO vo = new WorkOrderVO();
            fillVO(vo, row, employeeNames, stationNames, logs.getOrDefault(row.getId(), List.of()), now);
            list.add(vo);
        }
        return list;
    }

    private WorkOrderDetailVO toDetail(WorkOrder order, LocalDateTime now) {
        Set<Long> employeeIds = new LinkedHashSet<>();
        addIfNotNull(employeeIds, order.getReporterId());
        addIfNotNull(employeeIds, order.getAssigneeId());
        Map<Long, String> employeeNames = employeeNames(employeeIds);
        // 用 singletonList 而非 List.of：stationId 可能为 null（List.of 不接受 null 元素）
        List<Long> stationIds = Collections.singletonList(order.getStationId());
        Map<Long, String> stationNames = stationNames(stationIds);

        WorkOrderDetailVO vo = new WorkOrderDetailVO();
        fillVO(vo, order, employeeNames, stationNames, timelineByOrder(Collections.singletonList(order.getId()))
                .getOrDefault(order.getId(), List.of()), now);
        vo.setTransfers(transfersByOrder(order.getId()));
        return vo;
    }

    private void fillVO(WorkOrderVO vo, WorkOrder order, Map<Long, String> employeeNames,
                        Map<Long, String> stationNames, List<WorkOrderTimeline> logs, LocalDateTime now) {
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setType(order.getType());
        vo.setStatus(order.getStatus());
        vo.setPriority(order.getPriority());
        vo.setTitle(order.getTitle());
        vo.setContent(order.getContent());
        vo.setSource(order.getSource() == null ? WorkOrderConstants.SOURCE_MANUAL : order.getSource());
        vo.setStationId(order.getStationId());
        vo.setStationName(stationNames.get(order.getStationId()));
        vo.setParcelId(order.getParcelId());
        vo.setWaybillNo(order.getWaybillNo());
        vo.setReporterId(order.getReporterId());
        vo.setReporterName(employeeNames.get(order.getReporterId()));
        vo.setAssigneeId(order.getAssigneeId());
        vo.setAssigneeName(employeeNames.get(order.getAssigneeId()));
        vo.setSlaDeadline(order.getSlaDeadline());
        boolean overdue = WorkOrderSlaPolicy.isOverdueUnhandled(order.getStatus(), order.getSlaDeadline(), now);
        vo.setOverdueUnhandled(overdue);
        // 旧别名与新名同值（Mock 并存；TODO(扩展): 前端统一改用 overdueUnhandled 后删除 overSla）
        vo.setOverSla(overdue);
        vo.setResolvedTime(order.getResolvedTime());
        vo.setClosedTime(order.getClosedTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        List<HandleLogVO> handleLog = new ArrayList<>(logs.size());
        for (WorkOrderTimeline log : logs) {
            HandleLogVO item = new HandleLogVO();
            item.setTime(log.getTime());
            item.setAction(log.getAction());
            item.setOperatorName(log.getOperatorName());
            item.setContent(log.getContent());
            handleLog.add(item);
        }
        vo.setHandleLog(handleLog);
    }

    private DispatchRuleVO toRuleVO(WorkOrderDispatchRule rule) {
        DispatchRuleVO vo = new DispatchRuleVO();
        vo.setId(rule.getId());
        vo.setKeyword(rule.getKeyword());
        vo.setWorkOrderType(rule.getWorkOrderType());
        vo.setPriority(rule.getPriority());
        vo.setDefaultAssigneeId(rule.getDefaultAssigneeId());
        vo.setEnabled(rule.getEnabled() != null && rule.getEnabled() == 1);
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    // ==================== 内部：留痕 / 通知 ====================

    private void pushTimeline(Long orderId, String action, Long operatorId, String operatorName,
                              String content, LocalDateTime time) {
        WorkOrderTimeline row = new WorkOrderTimeline();
        row.setWorkOrderId(orderId);
        row.setAction(action);
        row.setOperatorId(operatorId);
        row.setOperatorName(operatorName);
        row.setContent(content);
        row.setTime(time);
        timelineMapper.insert(row);
    }

    private void notifyAssign(WorkOrder order, Employee assignee) {
        notificationService.sendSystem(assignee.getId(), WorkOrderConstants.NOTIFY_ASSIGN, "工单指派",
                "您被指派处理工单 " + order.getOrderNo() + "：" + order.getTitle(),
                WorkOrderConstants.BIZ_TYPE, order.getId());
    }

    // ==================== 内部：只读取数 ====================

    private WorkOrder findOrder(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return order;
    }

    private Employee loadEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return employee;
    }

    private String realName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        return employee == null ? null : employee.getRealName();
    }

    private Map<Long, String> employeeNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            // 用 emptyMap 而非 Map.of()：调用方可能以 null 键查询（上报人/处理人可为空），
            // Map.of() 的 get(null) 会抛 NPE，Collections.emptyMap().get(null) 返回 null
            return Collections.emptyMap();
        }
        List<Employee> rows = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .select(Employee::getId, Employee::getRealName)
                .in(Employee::getId, ids));
        Map<Long, String> map = new LinkedHashMap<>();
        for (Employee employee : rows) {
            map.put(employee.getId(), employee.getRealName());
        }
        return map;
    }

    private Map<Long, String> stationNames(Collection<Long> ids) {
        Set<Long> nonNull = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                nonNull.add(id);
            }
        }
        if (nonNull.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Station> rows = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .select(Station::getId, Station::getStationName)
                .in(Station::getId, nonNull));
        Map<Long, String> map = new LinkedHashMap<>();
        for (Station station : rows) {
            map.put(station.getId(), station.getStationName());
        }
        return map;
    }

    private Map<Long, List<WorkOrderTimeline>> timelineByOrder(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WorkOrderTimeline> rows = timelineMapper.selectList(new LambdaQueryWrapper<WorkOrderTimeline>()
                .in(WorkOrderTimeline::getWorkOrderId, orderIds)
                .orderByAsc(WorkOrderTimeline::getWorkOrderId)
                .orderByAsc(WorkOrderTimeline::getId));
        Map<Long, List<WorkOrderTimeline>> map = new LinkedHashMap<>();
        for (WorkOrderTimeline row : rows) {
            map.computeIfAbsent(row.getWorkOrderId(), key -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private List<WorkOrderTransferVO> transfersByOrder(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        // 按转单时间倒序（详情页「就近在上」，对齐 Mock listWorkOrderTransfers）
        List<WorkOrderTransfer> rows = transferMapper.selectList(new LambdaQueryWrapper<WorkOrderTransfer>()
                .eq(WorkOrderTransfer::getWorkOrderId, orderId)
                .orderByDesc(WorkOrderTransfer::getTransferTime)
                .orderByDesc(WorkOrderTransfer::getId));
        List<WorkOrderTransferVO> list = new ArrayList<>(rows.size());
        for (WorkOrderTransfer row : rows) {
            WorkOrderTransferVO vo = new WorkOrderTransferVO();
            vo.setId(row.getId());
            vo.setWorkOrderId(row.getWorkOrderId());
            vo.setFromEmployeeId(row.getFromEmployeeId());
            vo.setFromEmployeeName(row.getFromEmployeeName());
            vo.setToEmployeeId(row.getToEmployeeId());
            vo.setToEmployeeName(row.getToEmployeeName());
            vo.setReason(row.getReason());
            vo.setOperatorId(row.getOperatorId());
            vo.setOperatorName(row.getOperatorName());
            vo.setTransferTime(row.getTransferTime());
            list.add(vo);
        }
        return list;
    }

    // ==================== 内部：小工具 ====================

    private LoginUser currentUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private String buildOrderNo(Long id, LocalDateTime date) {
        return "WO-" + date.format(DATE_FMT).replace("-", "") + "-" + String.format("%04d", id);
    }

    private String placeholderOrderNo() {
        // 单号列无唯一索引，占位值不会与其他行冲突；插入后立即回填正式单号
        return WorkOrderConstants.ORDER_NO_PLACEHOLDER_PREFIX + UUID.randomUUID();
    }

    /** 群消息标题截断（Mock titleFromContent：超 40 字截断加省略号） */
    private String titleFromContent(String content) {
        return content.length() > WorkOrderConstants.GROUP_TITLE_MAX
                ? content.substring(0, WorkOrderConstants.GROUP_TITLE_MAX) + "…"
                : content;
    }

    /** 消息发送时间解析：失败或空白回落到当前时间（对齐 Mock） */
    private LocalDateTime parseSendTime(String sendTime) {
        if (isBlank(sendTime)) {
            return LocalDateTime.now();
        }
        String text = sendTime.trim();
        try {
            if (text.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(text, DATE_TIME_FMT);
            }
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(text, DATE_FMT).atStartOfDay();
            }
        } catch (RuntimeException e) {
            log.warn("企微群消息 sendTime 解析失败，按当前时间处理：{}", sendTime);
        }
        return LocalDateTime.now();
    }

    private boolean overdueOnly(WorkOrderQuery query) {
        String flag = isBlank(query.getOverdueUnhandled()) ? query.getOverSla() : query.getOverdueUnhandled();
        return "1".equals(flag) || "true".equals(flag);
    }

    private boolean isEnabled(Employee employee) {
        return employee.getStatus() != null && employee.getStatus() == 1;
    }

    private Long parseStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addIfNotNull(Set<Long> target, Long value) {
        if (value != null) {
            target.add(value);
        }
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String orDash(String value) {
        return value == null ? "-" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }
}
