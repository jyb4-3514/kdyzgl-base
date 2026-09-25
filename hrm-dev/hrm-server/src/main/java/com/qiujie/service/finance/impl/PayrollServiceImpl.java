package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.finance.MyPayrollQuery;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.dto.finance.PayrollItemAdjustRequest;
import com.qiujie.dto.finance.PayrollPublishRequest;
import com.qiujie.dto.finance.PayrollQuery;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollItem;
import com.qiujie.entity.PayrollRule;
import com.qiujie.entity.PayrollRuleItem;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.PayrollItemMapper;
import com.qiujie.mapper.PayrollMapper;
import com.qiujie.mapper.PayrollRuleItemMapper;
import com.qiujie.mapper.PayrollRuleMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.finance.PayrollService;
import com.qiujie.service.finance.support.PayrollBillType;
import com.qiujie.service.finance.support.PayrollCalcContext;
import com.qiujie.service.finance.support.PayrollGenerateGuard;
import com.qiujie.service.finance.support.PayrollItemAmount;
import com.qiujie.service.finance.support.PayrollItemDraft;
import com.qiujie.service.finance.support.PayrollItemType;
import com.qiujie.service.finance.support.PayrollNoGenerator;
import com.qiujie.service.finance.support.PayrollResolverRegistry;
import com.qiujie.service.finance.support.PayrollSnapshotBuilder;
import com.qiujie.service.finance.support.PayrollSource;
import com.qiujie.service.finance.support.PayrollStateMachine;
import com.qiujie.service.finance.support.PayrollStatus;
import com.qiujie.service.finance.support.PayrollTotals;
import com.qiujie.service.finance.support.PayrollTotalsPolicy;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementRef;
import com.qiujie.util.UserContext;
import com.qiujie.vo.finance.MyPayrollPageVO;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollItemVO;
import com.qiujie.vo.finance.PayrollPageVO;
import com.qiujie.vo.finance.PayrollPublishVO;
import com.qiujie.vo.finance.PayrollSubmitVO;
import com.qiujie.vo.finance.PayrollVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 工资单服务实现（对齐 Mock {@code financeStore} 工资单段 + {@code routes/finance.js}）。
 * <p>
 * <b>算薪内核不含任何计薪项专属公式</b>：金额一律由 {@link PayrollResolverRegistry} 按规则项来源解析，
 * 合计由 {@link PayrollTotalsPolicy} 统一计算（规则驱动，算法 S2）。
 * 状态流转集中走 {@link PayrollStateMachine}；生成幂等走 {@link PayrollGenerateGuard}。
 * <p>
 * 金额参数（{@code capRatio} 默认、未知来源策略、{@code cap} 语义、是否允许负净额）全部取
 * {@code hrm.algo.payroll.*}，代码内不内联阈值（规则 §11.4、反模式 A03）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    /** 员工可见状态：未发布前工资单不能让本人看到（列表与详情共用） */
    private static final List<String> EMPLOYEE_VISIBLE_STATUS =
            List.of(PayrollStatus.PUBLISHED.name(), PayrollStatus.CONFIRMED.name());

    private final PayrollMapper payrollMapper;
    private final PayrollItemMapper payrollItemMapper;
    private final PayrollRuleMapper payrollRuleMapper;
    private final PayrollRuleItemMapper payrollRuleItemMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final PayrollContextProvider contextProvider;
    private final PayrollResolverRegistry resolverRegistry;
    private final AlgoProperties algoProperties;

    // ==================== 我的工资单 ====================

    @Override
    @Transactional(readOnly = true)
    public MyPayrollPageVO myPayrolls(MyPayrollQuery query) {
        MyPayrollQuery safe = query == null ? new MyPayrollQuery() : query;
        String month = safe.getMonth();
        if (notBlank(month) && !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        String status = safe.getStatus();
        if (notBlank(status) && !EMPLOYEE_VISIBLE_STATUS.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<Payroll> wrapper = new LambdaQueryWrapper<Payroll>()
                .eq(Payroll::getEmployeeId, userId)
                .in(Payroll::getStatus, EMPLOYEE_VISIBLE_STATUS);
        if (notBlank(month)) {
            wrapper.eq(Payroll::getMonth, month);
        }
        if (notBlank(status)) {
            wrapper.eq(Payroll::getStatus, status);
        }
        wrapper.orderByDesc(Payroll::getMonth).orderByDesc(Payroll::getId);

        Page<Payroll> page = payrollMapper.selectPage(new Page<>(pageNo(safe.getPageNum()), pageSize(safe.getPageSize())), wrapper);

        MyPayrollPageVO vo = new MyPayrollPageVO();
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setList(assembleAll(page.getRecords()));
        vo.setEmployeeId(userId);
        return vo;
    }

    // ==================== 生成 / 提交 / 发布 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollGenerateVO generate(PayrollGenerateRequest request) {
        PayrollGenerateRequest safe = request == null ? new PayrollGenerateRequest() : request;
        String month = safe.getMonth();
        if (!notBlank(month) || !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        PayrollRule rule = activeRule(safe.getRuleId());
        if (rule == null) {
            throw new BusinessException(ErrorCode.FINANCE_RULE_NOT_EXISTS);
        }
        // 幂等：同月任一月度单已提交/已发布 → 整批拒绝（不得部分成功）
        List<Payroll> monthMonthly = payrollMapper.selectList(new LambdaQueryWrapper<Payroll>()
                .select(Payroll::getId, Payroll::getPayrollNo, Payroll::getMonth, Payroll::getBillType, Payroll::getStatus)
                .eq(Payroll::getMonth, month)
                .eq(Payroll::getBillType, PayrollBillType.MONTHLY.name()));
        Payroll blocked = PayrollGenerateGuard.findBlockingMonthly(monthMonthly, month);
        if (blocked != null) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_GENERATED,
                    "该月工资单已提交审核或已发布（" + blocked.getPayrollNo() + "），不可重复生成");
        }

        List<Employee> employees = activeEmployees(safe);
        List<PayrollRuleItem> allItems = loadRuleItems(rule.getId());
        List<PayrollRuleItem> enabledItems = enabledSorted(allItems);
        String snapshot = PayrollSnapshotBuilder.build(rule, allItems);

        List<Long> payrollIds = new ArrayList<>();
        for (Employee employee : employees) {
            // 覆盖重建：物理删除同员工同月同类型旧单（含明细）后再生成，保证重复调用幂等
            deleteExisting(employee.getId(), month, PayrollBillType.MONTHLY.name());
            Payroll created = createPayroll(employee, month, PayrollBillType.MONTHLY.name(), rule,
                    enabledItems, snapshot, null, null, PayrollStatus.DRAFT.name());
            payrollIds.add(created.getId());
        }

        PayrollGenerateVO vo = new PayrollGenerateVO();
        vo.setMonth(month);
        vo.setRuleId(rule.getId());
        vo.setRuleName(rule.getRuleName());
        vo.setCreated(payrollIds.size());
        vo.setPayrollIds(payrollIds);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollSubmitVO submit(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ids 须为非空数组");
        }
        // 先全量校验，再统一更新（避免部分成功）
        for (Long id : ids) {
            requireAction(id, PayrollStateMachine.ACTION_SUBMIT);
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                    .eq(Payroll::getId, id)
                    .set(Payroll::getStatus, PayrollStatus.PENDING_APPROVAL.name())
                    .set(Payroll::getApproveRemark, null)
                    .set(Payroll::getObjectionReason, null)
                    .set(Payroll::getObjectionTime, null)
                    .set(Payroll::getConfirmTime, null)
                    .set(Payroll::getUpdateTime, now));
        }
        PayrollSubmitVO vo = new PayrollSubmitVO();
        vo.setSubmitted(ids.size());
        vo.setPayrollIds(ids);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollPublishVO publish(PayrollPublishRequest request) {
        PayrollPublishRequest safe = request == null ? new PayrollPublishRequest() : request;
        List<Long> ids = safe.getIds();
        String month = safe.getMonth();
        boolean hasIds = ids != null && !ids.isEmpty();
        if (!hasIds && !notBlank(month)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请传入 ids 或 month");
        }
        if (notBlank(month) && !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }

        List<Payroll> targets = new ArrayList<>();
        if (hasIds) {
            for (Long id : ids) {
                Payroll payroll = payrollMapper.selectById(id);
                if (payroll != null) {
                    targets.add(payroll);
                }
            }
        } else {
            LambdaQueryWrapper<Payroll> wrapper = new LambdaQueryWrapper<Payroll>()
                    .eq(Payroll::getMonth, month)
                    .eq(Payroll::getStatus, PayrollStatus.APPROVED.name());
            if (safe.getStationId() != null) {
                wrapper.eq(Payroll::getStationId, safe.getStationId());
            }
            targets = payrollMapper.selectList(wrapper);
        }

        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        int published = 0;
        int skipped = 0;
        List<Long> publishedIds = new ArrayList<>();
        for (Payroll payroll : targets) {
            if (!PayrollStatus.APPROVED.name().equals(payroll.getStatus())) {
                skipped++;
                continue;
            }
            payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                    .eq(Payroll::getId, payroll.getId())
                    .set(Payroll::getStatus, PayrollStatus.PUBLISHED.name())
                    .set(Payroll::getPublisherId, operator.id())
                    .set(Payroll::getPublisherName, operator.name())
                    .set(Payroll::getPublishTime, now)
                    .set(Payroll::getUpdateTime, now));
            published++;
            publishedIds.add(payroll.getId());
        }

        PayrollPublishVO vo = new PayrollPublishVO();
        vo.setPublished(published);
        vo.setSkipped(skipped);
        vo.setPayrollIds(publishedIds);
        return vo;
    }

    // ==================== 列表 / 详情 ====================

    @Override
    @Transactional(readOnly = true)
    public PayrollPageVO list(PayrollQuery query) {
        PayrollQuery safe = query == null ? new PayrollQuery() : query;
        String month = safe.getMonth();
        if (notBlank(month) && !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        if (notBlank(safe.getStatus()) && !PayrollStatus.isValid(safe.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }
        if (notBlank(safe.getBillType()) && !PayrollBillType.isValid(safe.getBillType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billType 取值非法");
        }
        List<Long> keywordEmployeeIds = keywordEmployeeIds(safe.getKeyword());

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PayrollStatus status : PayrollStatus.values()) {
            Long count = payrollMapper.selectCount(buildWrapper(safe, false, keywordEmployeeIds)
                    .eq(Payroll::getStatus, status.name()));
            counts.put(status.name(), count == null ? 0 : count.intValue());
        }

        Page<Payroll> page = payrollMapper.selectPage(
                new Page<>(pageNo(safe.getPageNum()), pageSize(safe.getPageSize())),
                buildWrapper(safe, true, keywordEmployeeIds)
                        .orderByDesc(Payroll::getMonth).orderByAsc(Payroll::getEmployeeId).orderByAsc(Payroll::getId));

        PayrollPageVO vo = new PayrollPageVO();
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setList(assembleAll(page.getRecords()));
        vo.setCounts(counts);
        vo.setMonth(notBlank(month) ? month : null);
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollVO detail(Long id) {
        Payroll payroll = requirePayroll(id);
        String role = UserContext.getRole();
        if (!RoleEnum.isAdmin(role)) {
            if (!Objects.equals(payroll.getEmployeeId(), UserContext.getUserId())) {
                throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION);
            }
            if (!EMPLOYEE_VISIBLE_STATUS.contains(payroll.getStatus())) {
                throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "工资单尚未发布，暂不可查看");
            }
        }
        return toVO(payroll);
    }

    // ==================== 改人工项 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO updateItems(Long id, List<PayrollItemAdjustRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "items 须为非空数组");
        }
        Payroll payroll = requirePayroll(id);
        if (!PayrollStateMachine.isEditable(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                    "当前状态（" + PayrollStatus.labelOf(payroll.getStatus()) + "）不允许修改金额");
        }
        List<PayrollItem> rows = loadItems(id);
        Map<String, PayrollItem> byKey = new HashMap<>();
        for (PayrollItem row : rows) {
            byKey.put(row.getItemKey(), row);
        }
        for (PayrollItemAdjustRequest adjust : items) {
            PayrollItem target = byKey.get(adjust.getKey());
            if (target == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "工资单项不存在：" + adjust.getKey());
            }
            if (!PayrollSource.MANUAL.name().equals(target.getSource())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "「" + target.getItemName() + "」由规则计算，不可手工修改");
            }
            BigDecimal amount = coerceAmount(adjust.getAmount());
            if (amount == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "「" + target.getItemName() + "」金额须为数字");
            }
            target.setAmount(amount);
            target.setDetail("人工填写");
            payrollItemMapper.updateById(target);
        }

        List<PayrollItemDraft> drafts = new ArrayList<>(rows.size());
        for (PayrollItem row : rows) {
            drafts.add(new PayrollItemDraft(row.getItemKey(), row.getItemName(), row.getItemType(),
                    row.getSource(), row.getAmount(), row.getDetail()));
        }
        PayrollTotals totals = PayrollTotalsPolicy.of(drafts, algoProperties.getPayroll().isAllowNegativeNet());
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getAdditionTotal, totals.additionTotal())
                .set(Payroll::getDeductionTotal, totals.deductionTotal())
                .set(Payroll::getGrossAmount, totals.grossAmount())
                .set(Payroll::getNetAmount, totals.netAmount())
                .set(Payroll::getUpdateTime, LocalDateTime.now()));
        return toVO(requirePayroll(id));
    }

    // ==================== 审核 / 确认 / 异议 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO approve(Long id, Boolean approved, String approveRemark) {
        if (approved == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approved 须为布尔值");
        }
        if (approveRemark != null && !approveRemark.isBlank() && approveRemark.trim().length() > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审核意见不可超过 200 字");
        }
        Payroll payroll = requireAction(id, approved ? PayrollStateMachine.ACTION_APPROVE : PayrollStateMachine.ACTION_REJECT);
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        String storedRemark = (approveRemark == null || approveRemark.isBlank()) ? null : approveRemark.trim();
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, payroll.getId())
                .set(Payroll::getStatus, approved ? PayrollStatus.APPROVED.name() : PayrollStatus.REJECTED.name())
                .set(Payroll::getApproveRemark, storedRemark)
                .set(Payroll::getApproverId, operator.id())
                .set(Payroll::getApproverName, operator.name())
                .set(Payroll::getApproveTime, now)
                .set(Payroll::getUpdateTime, now));
        return toVO(requirePayroll(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO confirm(Long id) {
        Payroll payroll = requirePayroll(id);
        if (!Objects.equals(payroll.getEmployeeId(), UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION);
        }
        if (PayrollStatus.CONFIRMED.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "工资单已确认，无需重复确认");
        }
        if (!PayrollStatus.PUBLISHED.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "工资单尚未发布，暂不可确认");
        }
        LocalDateTime now = LocalDateTime.now();
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getStatus, PayrollStatus.CONFIRMED.name())
                .set(Payroll::getConfirmTime, now)
                .set(Payroll::getUpdateTime, now));
        return toVO(requirePayroll(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO objection(Long id, String reason) {
        if (reason == null || reason.trim().length() < 2 || reason.trim().length() > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "异议原因长度须为 2-200");
        }
        Payroll payroll = requirePayroll(id);
        if (!Objects.equals(payroll.getEmployeeId(), UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION);
        }
        if (!PayrollStatus.PUBLISHED.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "仅已发布的工资单可提异议");
        }
        LocalDateTime now = LocalDateTime.now();
        // 退回待审核：清空确认/发布时间与发布人（与 Mock 一致，避免时间线自相矛盾）
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getStatus, PayrollStatus.PENDING_APPROVAL.name())
                .set(Payroll::getObjectionReason, reason.trim())
                .set(Payroll::getObjectionTime, now)
                .set(Payroll::getConfirmTime, null)
                .set(Payroll::getPublishTime, null)
                .set(Payroll::getPublisherId, null)
                .set(Payroll::getPublisherName, null)
                .set(Payroll::getUpdateTime, now));
        return toVO(requirePayroll(id));
    }

    // ==================== 离职结算单（P5 端口） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollSettlementRef createSettlement(PayrollSettlementCommand command) {
        Long employeeId = command == null ? null : command.getEmployeeId();
        String month = command == null ? null : command.getMonth();
        PayrollRule rule = activeRule(null);
        if (rule == null) {
            throw new BusinessException(ErrorCode.FINANCE_RULE_NOT_EXISTS);
        }
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        // 账期由人事域按「最后工作日所在月」传入，此处仅防御性校验（避免 NOT NULL 落库失败）
        if (!notBlank(month) || !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        // 幂等：同员工同月结算单只生成一次
        List<Payroll> existing = payrollMapper.selectList(new LambdaQueryWrapper<Payroll>()
                .eq(Payroll::getEmployeeId, employeeId)
                .eq(Payroll::getMonth, month)
                .eq(Payroll::getBillType, PayrollBillType.SETTLEMENT.name()));
        if (!existing.isEmpty()) {
            Payroll hit = existing.get(0);
            return new PayrollSettlementRef(hit.getId(), hit.getPayrollNo(), hit.getNetAmount());
        }

        List<PayrollRuleItem> allItems = loadRuleItems(rule.getId());
        List<PayrollRuleItem> enabledItems = enabledSorted(allItems);
        String snapshot = PayrollSnapshotBuilder.build(rule, allItems);
        Payroll created = createPayroll(employee, month, PayrollBillType.SETTLEMENT.name(), rule, enabledItems, snapshot,
                command.getOffboardingId(), command.getRemark(), PayrollStatus.DRAFT.name());
        return new PayrollSettlementRef(created.getId(), created.getPayrollNo(), created.getNetAmount());
    }

    // ==================== 私有：算薪内核 ====================

    /** 建单：解析明细 → 合计 → 落库（工资单 + 明细）；返回已落库实体 */
    private Payroll createPayroll(Employee employee, String month, String billType, PayrollRule rule,
                                  List<PayrollRuleItem> enabledItems, String snapshot,
                                  Long offboardingId, String remark, String status) {
        PayrollCalcContext ctx = contextProvider.contextOf(employee.getId(), month);
        List<PayrollItemDraft> drafts = resolveItems(enabledItems, ctx);
        PayrollTotals totals = PayrollTotalsPolicy.of(drafts, algoProperties.getPayroll().isAllowNegativeNet());

        Payroll payroll = new Payroll();
        payroll.setPayrollNo(PayrollNoGenerator.of(billType, month, employee.getId()));
        payroll.setEmployeeId(employee.getId());
        payroll.setStationId(employee.getStationId());
        payroll.setMonth(month);
        payroll.setBillType(billType);
        payroll.setRuleId(rule.getId());
        payroll.setRuleName(rule.getRuleName());
        payroll.setRuleSnapshot(snapshot);
        payroll.setAdditionTotal(totals.additionTotal());
        payroll.setDeductionTotal(totals.deductionTotal());
        payroll.setGrossAmount(totals.grossAmount());
        payroll.setNetAmount(totals.netAmount());
        payroll.setStatus(status);
        payroll.setRemark(remark);
        payroll.setOffboardingId(offboardingId);
        payrollMapper.insert(payroll);

        int sortOrder = 1;
        for (PayrollItemDraft draft : drafts) {
            PayrollItem item = new PayrollItem();
            item.setPayrollId(payroll.getId());
            item.setItemKey(draft.key());
            item.setItemName(draft.name());
            item.setItemType(draft.type());
            item.setSource(draft.source());
            item.setAmount(draft.amount());
            item.setDetail(draft.detail());
            item.setSortOrder(sortOrder++);
            payrollItemMapper.insert(item);
        }
        return payroll;
    }

    /** 逐项解析（按 sortOrder 顺序分发注册表；未知来源按策略处理，不中断整批） */
    private List<PayrollItemDraft> resolveItems(List<PayrollRuleItem> enabledItems, PayrollCalcContext ctx) {
        List<PayrollItemDraft> drafts = new ArrayList<>(enabledItems.size());
        for (PayrollRuleItem item : enabledItems) {
            PayrollItemAmount resolved = resolverRegistry.resolve(item.getSource(), item.getParams(), ctx);
            drafts.add(new PayrollItemDraft(item.getItemKey(), item.getItemName(), item.getItemType(),
                    item.getSource(), resolved.amount(), resolved.detail()));
        }
        return drafts;
    }

    // ==================== 私有：规则 / 员工 / 幂等 ====================

    /** 生效规则：指定取指定，未指定取第一个启用规则（Mock {@code activeRule}） */
    private PayrollRule activeRule(Long ruleId) {
        if (ruleId != null) {
            return payrollRuleMapper.selectById(ruleId);
        }
        List<PayrollRule> enabled = payrollRuleMapper.selectList(new LambdaQueryWrapper<PayrollRule>()
                .eq(PayrollRule::getStatus, 1)
                .orderByAsc(PayrollRule::getId));
        return enabled.isEmpty() ? null : enabled.get(0);
    }

    private List<PayrollRuleItem> loadRuleItems(Long ruleId) {
        return payrollRuleItemMapper.selectList(new LambdaQueryWrapper<PayrollRuleItem>()
                .eq(PayrollRuleItem::getRuleId, ruleId)
                .orderByAsc(PayrollRuleItem::getSortOrder).orderByAsc(PayrollRuleItem::getId));
    }

    /** 启用项按 sortOrder、id 升序（Mock {@code buildItems} 的过滤 + 排序） */
    private List<PayrollRuleItem> enabledSorted(List<PayrollRuleItem> allItems) {
        List<PayrollRuleItem> enabled = new ArrayList<>();
        for (PayrollRuleItem item : allItems) {
            if (item.getEnabled() != null && item.getEnabled() == 1) {
                enabled.add(item);
            }
        }
        return enabled;
    }

    /** 在职员工（status=1），按 id 升序；可按员工/驿站/部门筛选（对齐 Mock {@code generatePayrolls}） */
    private List<Employee> activeEmployees(PayrollGenerateRequest request) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .select(Employee::getId, Employee::getRealName, Employee::getStationId, Employee::getRole)
                .eq(Employee::getStatus, 1)
                .orderByAsc(Employee::getId);
        if (request.getEmployeeIds() != null && !request.getEmployeeIds().isEmpty()) {
            wrapper.in(Employee::getId, new ArrayList<>(new LinkedHashSet<>(request.getEmployeeIds())));
        }
        if (request.getStationId() != null) {
            wrapper.eq(Employee::getStationId, request.getStationId());
        }
        if (request.getDeptId() != null) {
            wrapper.eq(Employee::getDeptId, request.getDeptId());
        }
        return employeeMapper.selectList(wrapper);
    }

    /**
     * 覆盖重建：<b>物理</b>删除同员工同月同类型旧单及其明细，供重复生成幂等。
     * <p>
     * 为什么不用 {@code payrollMapper.delete(wrapper)}：{@link Payroll} 标注 {@code @TableLogic}
     * 且 application.yml 全局开启逻辑删除，该调用只把旧行 {@code is_deleted} 置 1，物理行仍在，
     * 重复生成只增不减（本缺陷根因：同账期连跑两次 → 物理行 63 → 126）。故改走自定义物理删除。
     * <p>
     * 范围收口：只清「可覆盖」状态（DRAFT/REJECTED，口径见 {@link PayrollGenerateGuard}），
     * 已提交/已发布单据不取不删（9405 语义不变）；同范围的历史逻辑删除残留行一并清除，
     * 使重跑后 {@code COUNT(*) == COUNT(DISTINCT employee_id)}。删除顺序：先明细后主单，杜绝孤儿。
     */
    private void deleteExisting(Long employeeId, String month, String billType) {
        List<Long> ids = payrollMapper.selectRebuildTargetIds(employeeId, month, billType,
                PayrollGenerateGuard.editableStatuses());
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先删明细再删主单：payroll_item 无物理外键，靠顺序与范围保证不留孤儿
        payrollItemMapper.deletePhysicallyByPayrollIds(ids);
        payrollMapper.deletePhysicallyByIds(ids);
    }

    // ==================== 私有：状态守卫 / 操作人 ====================

    private Payroll requirePayroll(Long id) {
        Payroll payroll = id == null ? null : payrollMapper.selectById(id);
        if (payroll == null) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NOT_EXISTS);
        }
        return payroll;
    }

    /** 状态守卫：读单 → 校验动作在该状态下是否允许（入口统一，避免每个动作各写一遍） */
    private Payroll requireAction(Long id, String action) {
        Payroll payroll = requirePayroll(id);
        if (!PayrollStateMachine.allows(payroll.getStatus(), action)) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                    "当前状态（" + PayrollStatus.labelOf(payroll.getStatus()) + "）不允许该操作");
        }
        return payroll;
    }

    private Operator currentOperator() {
        Long id = UserContext.getUserId();
        LoginUser user = UserContext.get();
        String name = null;
        if (id != null) {
            Employee employee = employeeMapper.selectById(id);
            name = employee == null ? null : employee.getRealName();
        }
        if (name == null && user != null) {
            name = user.getUsername();
        }
        return new Operator(id, name);
    }

    // ==================== 私有：查询装配 ====================

    /** 构造列表/计数筛选（keyword 匹配工资单号 or 员工姓名对应的员工 id 集） */
    private LambdaQueryWrapper<Payroll> buildWrapper(PayrollQuery query, boolean includeStatus,
                                                     List<Long> keywordEmployeeIds) {
        LambdaQueryWrapper<Payroll> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(query.getMonth())) {
            wrapper.eq(Payroll::getMonth, query.getMonth());
        }
        if (query.getStationId() != null) {
            wrapper.eq(Payroll::getStationId, query.getStationId());
        }
        if (query.getEmployeeId() != null) {
            wrapper.eq(Payroll::getEmployeeId, query.getEmployeeId());
        }
        if (includeStatus && notBlank(query.getStatus())) {
            wrapper.eq(Payroll::getStatus, query.getStatus());
        }
        if (notBlank(query.getBillType())) {
            wrapper.eq(Payroll::getBillType, query.getBillType());
        }
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        if (!keyword.isEmpty()) {
            wrapper.and(inner -> {
                inner.like(Payroll::getPayrollNo, keyword);
                if (keywordEmployeeIds != null && !keywordEmployeeIds.isEmpty()) {
                    inner.or().in(Payroll::getEmployeeId, keywordEmployeeIds);
                }
            });
        }
        return wrapper;
    }

    private List<Long> keywordEmployeeIds(String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .select(Employee::getId)
                .like(Employee::getRealName, text));
        List<Long> ids = new ArrayList<>(employees.size());
        for (Employee employee : employees) {
            ids.add(employee.getId());
        }
        return ids;
    }

    private List<PayrollVO> assembleAll(List<Payroll> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> employeeIds = new ArrayList<>();
        List<Long> stationIds = new ArrayList<>();
        List<Long> payrollIds = new ArrayList<>();
        for (Payroll row : rows) {
            employeeIds.add(row.getEmployeeId());
            if (row.getStationId() != null) {
                stationIds.add(row.getStationId());
            }
            payrollIds.add(row.getId());
        }
        Map<Long, Employee> employees = loadEmployees(employeeIds);
        Map<Long, Station> stations = loadStations(stationIds);
        Map<Long, List<PayrollItem>> itemsByPayroll = loadItemsByPayroll(payrollIds);
        List<PayrollVO> list = new ArrayList<>(rows.size());
        for (Payroll row : rows) {
            list.add(assemble(row, employees, stations, itemsByPayroll.getOrDefault(row.getId(), List.of())));
        }
        return list;
    }

    private PayrollVO toVO(Payroll payroll) {
        Map<Long, Employee> employees = loadEmployees(List.of(payroll.getEmployeeId()));
        Map<Long, Station> stations = payroll.getStationId() == null
                ? Map.of() : loadStations(List.of(payroll.getStationId()));
        return assemble(payroll, employees, stations, loadItems(payroll.getId()));
    }

    private PayrollVO assemble(Payroll payroll, Map<Long, Employee> employees, Map<Long, Station> stations,
                               List<PayrollItem> items) {
        Employee employee = employees.get(payroll.getEmployeeId());
        Station station = payroll.getStationId() == null ? null : stations.get(payroll.getStationId());
        PayrollVO vo = new PayrollVO();
        vo.setId(payroll.getId());
        vo.setPayrollNo(payroll.getPayrollNo());
        vo.setEmployeeId(payroll.getEmployeeId());
        vo.setEmployeeName(employee == null || employee.getRealName() == null
                ? "员工" + payroll.getEmployeeId() : employee.getRealName());
        vo.setStationId(payroll.getStationId());
        vo.setStationName(station == null ? null : station.getStationName());
        vo.setMonth(payroll.getMonth());
        vo.setBillType(payroll.getBillType());
        vo.setBillTypeLabel(PayrollBillType.labelOf(payroll.getBillType()));
        vo.setRuleId(payroll.getRuleId());
        vo.setRuleName(payroll.getRuleName());
        List<PayrollItemVO> itemVOs = new ArrayList<>(items.size());
        for (PayrollItem item : items) {
            itemVOs.add(toItemVO(item));
        }
        vo.setItems(itemVOs);
        vo.setAdditionTotal(payroll.getAdditionTotal());
        vo.setDeductionTotal(payroll.getDeductionTotal());
        vo.setGrossAmount(payroll.getGrossAmount());
        vo.setNetAmount(payroll.getNetAmount());
        vo.setStatus(payroll.getStatus());
        vo.setStatusLabel(PayrollStatus.labelOf(payroll.getStatus()));
        vo.setRemark(payroll.getRemark());
        vo.setApproveRemark(payroll.getApproveRemark());
        vo.setApproverId(payroll.getApproverId());
        vo.setApproverName(payroll.getApproverName());
        vo.setApproveTime(payroll.getApproveTime());
        vo.setPublisherId(payroll.getPublisherId());
        vo.setPublisherName(payroll.getPublisherName());
        vo.setPublishTime(payroll.getPublishTime());
        vo.setConfirmTime(payroll.getConfirmTime());
        vo.setObjectionReason(payroll.getObjectionReason());
        vo.setObjectionTime(payroll.getObjectionTime());
        vo.setOffboardingId(payroll.getOffboardingId());
        vo.setActions(PayrollStateMachine.actionsOf(payroll.getStatus()));
        vo.setCreateTime(payroll.getCreateTime());
        vo.setUpdateTime(payroll.getUpdateTime());
        return vo;
    }

    private PayrollItemVO toItemVO(PayrollItem item) {
        PayrollItemVO vo = new PayrollItemVO();
        vo.setKey(item.getItemKey());
        vo.setName(item.getItemName());
        vo.setType(item.getItemType());
        vo.setTypeLabel(PayrollItemType.labelOf(item.getItemType()));
        vo.setSource(item.getSource());
        vo.setSourceLabel(PayrollSource.labelOf(item.getSource()));
        vo.setAmount(item.getAmount());
        vo.setDetail(item.getDetail());
        return vo;
    }

    private List<PayrollItem> loadItems(Long payrollId) {
        return payrollItemMapper.selectList(new LambdaQueryWrapper<PayrollItem>()
                .eq(PayrollItem::getPayrollId, payrollId)
                .orderByAsc(PayrollItem::getSortOrder).orderByAsc(PayrollItem::getId));
    }

    private Map<Long, List<PayrollItem>> loadItemsByPayroll(List<Long> payrollIds) {
        List<PayrollItem> rows = payrollItemMapper.selectList(new LambdaQueryWrapper<PayrollItem>()
                .in(PayrollItem::getPayrollId, payrollIds)
                .orderByAsc(PayrollItem::getSortOrder).orderByAsc(PayrollItem::getId));
        Map<Long, List<PayrollItem>> grouped = new LinkedHashMap<>();
        for (PayrollItem row : rows) {
            grouped.computeIfAbsent(row.getPayrollId(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private Map<Long, Employee> loadEmployees(List<Long> ids) {
        Map<Long, Employee> map = new HashMap<>();
        List<Long> valid = distinctNonNull(ids);
        if (valid.isEmpty()) {
            return map;
        }
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .select(Employee::getId, Employee::getRealName, Employee::getStationId)
                .in(Employee::getId, valid));
        for (Employee employee : employees) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    private Map<Long, Station> loadStations(List<Long> ids) {
        Map<Long, Station> map = new HashMap<>();
        List<Long> valid = distinctNonNull(ids);
        if (valid.isEmpty()) {
            return map;
        }
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .select(Station::getId, Station::getStationName)
                .in(Station::getId, valid));
        for (Station station : stations) {
            map.put(station.getId(), station);
        }
        return map;
    }

    // ==================== 私有：工具 ====================

    private List<Long> distinctNonNull(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    /** Object → BigDecimal（对齐 JS {@code Number(x)}：null/空串 → 0；非数字 → null） */
    private BigDecimal coerceAmount(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return BigDecimal.ZERO;
            }
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private int pageNo(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int pageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 操作人（id + 姓名快照；姓名取当前登录员工 real_name，取不到回落用户名） */
    private record Operator(Long id, String name) {
    }
}
