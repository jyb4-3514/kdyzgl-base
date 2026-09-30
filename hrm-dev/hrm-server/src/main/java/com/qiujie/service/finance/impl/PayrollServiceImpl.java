package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.finance.MyPayrollQuery;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.dto.finance.PayrollItemAddRequest;
import com.qiujie.dto.finance.PayrollItemAdjustRequest;
import com.qiujie.dto.finance.PayrollManualAdjustmentQuery;
import com.qiujie.dto.finance.PayrollPublishRequest;
import com.qiujie.dto.finance.PayrollQuery;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollItem;
import com.qiujie.entity.PayrollLog;
import com.qiujie.entity.PayrollRule;
import com.qiujie.entity.PayrollRuleItem;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.PayrollItemMapper;
import com.qiujie.mapper.PayrollLogMapper;
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
import com.qiujie.service.finance.support.PayrollLogAction;
import com.qiujie.service.finance.support.PayrollNoGenerator;
import com.qiujie.service.finance.support.PayrollNotifySupport;
import com.qiujie.service.finance.support.PayrollResolverRegistry;
import com.qiujie.service.finance.support.PayrollSnapshotBuilder;
import com.qiujie.service.finance.support.PayrollSource;
import com.qiujie.service.finance.support.PayrollStateMachine;
import com.qiujie.service.finance.support.PayrollStatus;
import com.qiujie.service.finance.support.PayrollTotals;
import com.qiujie.service.finance.support.PayrollTotalsPolicy;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementRef;
import com.qiujie.util.JsonUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.finance.MyPayrollPageVO;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollItemVO;
import com.qiujie.vo.finance.PayrollLogVO;
import com.qiujie.vo.finance.PayrollManualAdjustmentEmployeeVO;
import com.qiujie.vo.finance.PayrollManualAdjustmentSummaryVO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 工资单服务实现（对齐 Mock {@code financeStore} 工资单段 + {@code routes/finance.js}）。
 * <p>
 * <b>算薪内核不含任何计薪项专属公式</b>：金额一律由 {@link PayrollResolverRegistry} 按规则项来源解析，
 * 合计由 {@link PayrollTotalsPolicy} 统一计算（规则驱动，算法 S2）。
 * 状态流转集中走 {@link PayrollStateMachine}；生成幂等走 {@link PayrollGenerateGuard}；全链路留痕走 {@code payroll_log}。
 * <p>
 * 金额参数（{@code capRatio} 默认、未知来源策略、{@code cap} 语义、是否允许负净额）全部取
 * {@code hrm.algo.payroll.*}，代码内不内联阈值（规则 §11.4、反模式 A03）。
 * <p>
 * <b>终态冻结</b>：所有写入口进入业务分支前统一过 {@link PayrollStateMachine#assertMutable}（PAID → 9413）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    /** 员工可见状态：已发布 / 已确认 / 已发放（PAID 为归档态，员工确认后单据不应从列表消失，U-08） */
    private static final List<String> EMPLOYEE_VISIBLE_STATUS = List.of(
            PayrollStatus.PUBLISHED.name(), PayrollStatus.CONFIRMED.name(), PayrollStatus.PAID.name());
    /** 手工加扣款 item_key 前缀（R11 识别与 key 命名空间隔离的契约） */
    private static final String MANUAL_KEY_PREFIX = "MANUAL_";
    /** 事由长度区间（Q3 / M-3 / REG-03；越界回 9412） */
    private static final int REASON_MIN = 2;
    private static final int REASON_MAX = 200;

    private final PayrollMapper payrollMapper;
    private final PayrollItemMapper payrollItemMapper;
    private final PayrollRuleMapper payrollRuleMapper;
    private final PayrollRuleItemMapper payrollRuleItemMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final PayrollContextProvider contextProvider;
    private final PayrollResolverRegistry resolverRegistry;
    private final AlgoProperties algoProperties;
    private final PayrollLogMapper payrollLogMapper;
    private final PayrollNotifySupport notifySupport;

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
        List<Employee> employees = activeEmployees(safe);
        // 幂等：同月同驿站任一不可覆盖的月度单 → 整批拒绝（不得部分成功）。
        // 按驿站收敛（C-7 必改 1）：否则多驿站自动算薪时，A 站先跑出的单会把 B 站整批阻断。
        List<Payroll> monthMonthly = payrollMapper.selectList(new LambdaQueryWrapper<Payroll>()
                .eq(Payroll::getMonth, month)
                .eq(Payroll::getBillType, PayrollBillType.MONTHLY.name()));
        Payroll blocked = PayrollGenerateGuard.findBlockingMonthly(monthMonthly, month, targetStations(employees));
        if (blocked != null) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_GENERATED,
                    "该月工资单已提交审核或已发布（" + blocked.getPayrollNo() + "），不可重复生成");
        }
        Map<Long, Payroll> existingByEmployee = indexByEmployee(monthMonthly);

        List<PayrollRuleItem> allItems = loadRuleItems(rule.getId());
        List<PayrollRuleItem> enabledItems = enabledSorted(allItems);
        String snapshot = PayrollSnapshotBuilder.build(rule, allItems);
        Set<String> ruleKeys = ruleItemKeys(enabledItems);

        Operator operator = currentOperator();
        List<Long> payrollIds = new ArrayList<>();
        for (Employee employee : employees) {
            Payroll existing = existingByEmployee.get(employee.getId());
            // R11 甲：覆盖重建前先快照既有 source=MANUAL 明细（与删除同事务），避免手工项随重建丢失
            List<PayrollItemDraft> preserved = loadPreservedManual(existing);
            Set<String> usedKeys = new HashSet<>(ruleKeys);
            List<PayrollItemDraft> preservedFinal = new ArrayList<>(preserved.size());
            boolean keyRemapped = false;
            for (PayrollItemDraft draft : preserved) {
                if (usedKeys.contains(draft.key())) {
                    // 冲突：规则项 key 为真源保持不变，保留项由服务端重新生成 key（并留痕说明）
                    PayrollItemDraft remapped = new PayrollItemDraft(nextManualKey(month, usedKeys),
                            draft.name(), draft.type(), draft.source(), draft.amount(), draft.detail());
                    usedKeys.add(remapped.key());
                    preservedFinal.add(remapped);
                    keyRemapped = true;
                } else {
                    usedKeys.add(draft.key());
                    preservedFinal.add(draft);
                }
            }
            // 覆盖重建：物理删除同员工同月同类型旧单（含明细）后再生成，保证重复调用幂等
            List<Long> rebuildIds = payrollMapper.selectRebuildTargetIds(employee.getId(), month,
                    PayrollBillType.MONTHLY.name(), PayrollGenerateGuard.editableStatuses());
            deleteExisting(rebuildIds);
            Payroll created = createPayroll(employee, month, PayrollBillType.MONTHLY.name(), rule,
                    enabledItems, snapshot, null, null, PayrollStatus.DRAFT.name(), preservedFinal);
            payrollIds.add(created.getId());
            // 全链路留痕：每次生成（含覆盖重建）写 1 条；本批无调度器，一律 GENERATE_MANUAL
            writeLog(operator, created.getId(), employee.getId(), month, PayrollLogAction.GENERATE_MANUAL,
                    existing == null ? null : existing.getStatus(), created.getStatus(), null,
                    existing == null ? null : totalsSnapshot(existing),
                    generateSnapshot(created, preservedFinal),
                    keyRemapped ? "重建迁移改键：保留项的 item_key 与规则项冲突，已重新生成" : null);
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
        // 先全量校验（终态冻结 + 动作合法性），再统一更新（避免部分成功）
        List<Payroll> targets = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Payroll payroll = requirePayroll(id);
            PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_SUBMIT);
            if (!PayrollStateMachine.allows(payroll.getStatus(), PayrollStateMachine.ACTION_SUBMIT)) {
                throw statusInvalid(payroll);
            }
            targets.add(payroll);
        }
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        for (Payroll payroll : targets) {
            payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                    .eq(Payroll::getId, payroll.getId())
                    .set(Payroll::getStatus, PayrollStatus.PENDING_APPROVAL.name())
                    .set(Payroll::getApproveRemark, null)
                    .set(Payroll::getObjectionReason, null)
                    .set(Payroll::getObjectionTime, null)
                    .set(Payroll::getConfirmTime, null)
                    .set(Payroll::getUpdateTime, now));
            writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                    PayrollLogAction.SUBMIT, payroll.getStatus(), PayrollStatus.PENDING_APPROVAL.name(),
                    null, null, null, null);
        }
        PayrollSubmitVO vo = new PayrollSubmitVO();
        vo.setSubmitted(targets.size());
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

        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        int published = 0;
        int skipped = 0;
        List<Long> publishedIds = new ArrayList<>();

        if (hasIds) {
            // ids 路径：允许来源 = APPROVED（首发）或 OBJECTED（再发布）；非法来源显式报错，不得静默 skipped（REG-14）
            List<Payroll> targets = new ArrayList<>(ids.size());
            for (Long id : ids) {
                Payroll payroll = payrollMapper.selectById(id);
                if (payroll == null) {
                    continue;
                }
                PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_PUBLISH);
                String status = payroll.getStatus();
                if (!PayrollStatus.APPROVED.name().equals(status) && !PayrollStatus.OBJECTED.name().equals(status)) {
                    throw statusInvalid(payroll);
                }
                targets.add(payroll);
            }
            for (Payroll payroll : targets) {
                boolean republish = PayrollStatus.OBJECTED.name().equals(payroll.getStatus());
                publishOne(payroll.getId(), operator, now, republish);
                writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                        republish ? PayrollLogAction.REPUBLISH : PayrollLogAction.PUBLISH,
                        payroll.getStatus(), PayrollStatus.PUBLISHED.name(), null, null, null, null);
                published++;
                publishedIds.add(payroll.getId());
                notifyIfPublished(payroll.getId());
            }
        } else {
            // month 批量路径：维持仅 APPROVED（避免误批再发布），非 APPROVED 计入 skipped
            LambdaQueryWrapper<Payroll> wrapper = new LambdaQueryWrapper<Payroll>()
                    .eq(Payroll::getMonth, month)
                    .eq(Payroll::getStatus, PayrollStatus.APPROVED.name());
            if (safe.getStationId() != null) {
                wrapper.eq(Payroll::getStationId, safe.getStationId());
            }
            for (Payroll payroll : payrollMapper.selectList(wrapper)) {
                if (!PayrollStatus.APPROVED.name().equals(payroll.getStatus())) {
                    skipped++;
                    continue;
                }
                publishOne(payroll.getId(), operator, now, false);
                writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                        PayrollLogAction.PUBLISH, payroll.getStatus(), PayrollStatus.PUBLISHED.name(),
                        null, null, null, null);
                published++;
                publishedIds.add(payroll.getId());
                notifyIfPublished(payroll.getId());
            }
        }

        PayrollPublishVO vo = new PayrollPublishVO();
        vo.setPublished(published);
        vo.setSkipped(skipped);
        vo.setPayrollIds(publishedIds);
        return vo;
    }

    /** 发布/再发布落库：再发布清异议信息（异议历史永久留 payroll_log） */
    private void publishOne(Long id, Operator operator, LocalDateTime now, boolean republish) {
        LambdaUpdateWrapper<Payroll> update = new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getStatus, PayrollStatus.PUBLISHED.name())
                .set(Payroll::getPublisherId, operator.id())
                .set(Payroll::getPublisherName, operator.name())
                .set(Payroll::getPublishTime, now)
                .set(Payroll::getUpdateTime, now);
        if (republish) {
            update.set(Payroll::getObjectionReason, null).set(Payroll::getObjectionTime, null);
        }
        payrollMapper.update(null, update);
    }

    /**
     * 发布/再发布成功后投递类型 8（→员工本人）。
     * <p><b>前置强校验</b>：以 DB 落库状态为准，仅当确已落 {@code PUBLISHED} 才投递——即便
     * {@code publish} 分支异常导致未落库，也绝不把「草稿/待审」误推给员工（安全 §4.5 / M-7）。
     */
    private void notifyIfPublished(Long payrollId) {
        Payroll persisted = payrollMapper.selectById(payrollId);
        if (persisted != null && PayrollStatus.PUBLISHED.name().equals(persisted.getStatus())) {
            notifySupport.notifyPublished(persisted);
        }
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
        ensureVisible(payroll);
        return toVO(payroll);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollLogVO> logs(Long id) {
        Payroll payroll = requirePayroll(id);
        // 越权判定复用 detail() 的单一真源（employeeId==userId ∧ 状态 ∈ 可见集），杜绝按 id 遍历他人留痕（IDOR）
        ensureVisible(payroll);
        boolean admin = RoleEnum.isAdmin(UserContext.getRole());
        List<PayrollLog> rows = payrollLogMapper.selectList(new LambdaQueryWrapper<PayrollLog>()
                .eq(PayrollLog::getPayrollId, id)
                .orderByDesc(PayrollLog::getTime).orderByDesc(PayrollLog::getId));
        List<PayrollLogVO> list = new ArrayList<>(rows.size());
        for (PayrollLog row : rows) {
            list.add(toLogVO(row, admin));
        }
        return list;
    }

    /**
     * 非 ADMIN 的可见性守卫（单真源，{@code detail} 与 {@code logs} 共用）：
     * 非本人 → 9404；状态不在员工可见集（PUBLISHED/CONFIRMED/PAID）→ 9403。
     */
    private void ensureVisible(Payroll payroll) {
        if (RoleEnum.isAdmin(UserContext.getRole())) {
            return;
        }
        if (!Objects.equals(payroll.getEmployeeId(), UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION);
        }
        if (!EMPLOYEE_VISIBLE_STATUS.contains(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "工资单尚未发布，暂不可查看");
        }
    }

    // ==================== 改人工项 / 手工加扣款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO updateItems(Long id, String reason, List<PayrollItemAdjustRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "items 须为非空数组");
        }
        String trimmedReason = requireReason(reason);
        Payroll payroll = requirePayroll(id);
        PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_ITEM_UPDATE);
        if (!PayrollStateMachine.isItemEditable(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                    "当前状态（" + PayrollStatus.labelOf(payroll.getStatus()) + "）不允许修改金额");
        }
        List<PayrollItem> rows = loadItems(id);
        Map<String, PayrollItem> byKey = new HashMap<>();
        for (PayrollItem row : rows) {
            byKey.put(row.getItemKey(), row);
        }
        List<Map<String, Object>> changedBefore = new ArrayList<>();
        List<Map<String, Object>> changedAfter = new ArrayList<>();
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
            changedBefore.add(itemSnapshot(target));
            target.setAmount(amount);
            // 为什么不覆盖 detail：detail 存事由全文（REG-03），覆盖为「人工填写」会抹掉审计线索
            payrollItemMapper.updateById(target);
            changedAfter.add(itemSnapshot(target));
        }

        PayrollTotals totals = recalcTotals(rows);
        // before 快照须在 applyTotals 就地改内存态之前取，否则前后合计会写成同一个
        Map<String, Object> beforeSnapshot = adjustSnapshot(payroll, changedBefore);
        Payroll updated = applyTotals(payroll, totals);
        writeLog(currentOperator(), updated.getId(), updated.getEmployeeId(), updated.getMonth(),
                PayrollLogAction.ITEM_UPDATE, updated.getStatus(), updated.getStatus(), trimmedReason,
                beforeSnapshot, adjustSnapshot(updated, changedAfter), null);
        return toVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO addItem(Long id, PayrollItemAddRequest request) {
        PayrollItemAddRequest safe = request == null ? new PayrollItemAddRequest() : request;
        String trimmedReason = requireReason(safe.getReason());
        String itemType = safe.getItemType();
        if (!PayrollItemType.isValid(itemType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "itemType 取值非法");
        }
        String itemName = safe.getItemName() == null ? null : safe.getItemName().trim();
        if (!notBlank(itemName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "itemName 须为非空");
        }
        BigDecimal amount = coerceAmount(safe.getAmount());
        if (amount == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额须为数字");
        }
        if (amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额须大于 0");
        }

        Payroll payroll = requirePayroll(id);
        PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_ITEM_ADD);
        if (!PayrollStateMachine.isItemEditable(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                    "当前状态（" + PayrollStatus.labelOf(payroll.getStatus()) + "）不允许录入加扣款");
        }

        List<PayrollItem> rows = loadItems(id);
        Set<String> existingKeys = new HashSet<>();
        for (PayrollItem row : rows) {
            existingKeys.add(row.getItemKey());
        }
        // item_key 由服务端生成、强制 MANUAL_ 前缀（U-09）；生成后二次校验重复 → 9411（防御并发窗口 / 脏数据）
        String key = nextManualKey(payroll.getMonth(), existingKeys);
        Long duplicated = payrollItemMapper.selectCount(new LambdaQueryWrapper<PayrollItem>()
                .eq(PayrollItem::getPayrollId, id)
                .eq(PayrollItem::getItemKey, key));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_ITEM_EXISTS);
        }

        PayrollItem item = new PayrollItem();
        item.setPayrollId(id);
        item.setItemKey(key);
        item.setItemName(itemName);
        item.setItemType(itemType);
        item.setSource(PayrollSource.MANUAL.name());
        item.setAmount(amount);
        item.setDetail(notBlank(safe.getDetail()) ? safe.getDetail().trim() : "事由：" + trimmedReason);
        item.setSortOrder(nextSortOrder(rows));
        payrollItemMapper.insert(item);
        rows.add(item);

        PayrollTotals totals = recalcTotals(rows);
        Map<String, Object> beforeSnapshot = totalsSnapshot(payroll);
        Payroll updated = applyTotals(payroll, totals);
        writeLog(currentOperator(), updated.getId(), updated.getEmployeeId(), updated.getMonth(),
                PayrollLogAction.ITEM_ADD, updated.getStatus(), updated.getStatus(), trimmedReason,
                beforeSnapshot, adjustSnapshot(updated, List.of(itemSnapshot(item))), null);
        return toVO(updated);
    }

    // ==================== 审核 / 确认 / 异议 / 发放 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO approve(Long id, Boolean approved, String approveRemark) {
        if (approved == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approved 须为布尔值");
        }
        if (approveRemark != null && !approveRemark.isBlank() && approveRemark.trim().length() > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审核意见不可超过 200 字");
        }
        String action = approved ? PayrollStateMachine.ACTION_APPROVE : PayrollStateMachine.ACTION_REJECT;
        Payroll payroll = requirePayroll(id);
        PayrollStateMachine.assertMutable(payroll, action);
        if (!PayrollStateMachine.allows(payroll.getStatus(), action)) {
            throw statusInvalid(payroll);
        }
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        String storedRemark = (approveRemark == null || approveRemark.isBlank()) ? null : approveRemark.trim();
        String toStatus = approved ? PayrollStatus.APPROVED.name() : PayrollStatus.REJECTED.name();
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, payroll.getId())
                .set(Payroll::getStatus, toStatus)
                .set(Payroll::getApproveRemark, storedRemark)
                .set(Payroll::getApproverId, operator.id())
                .set(Payroll::getApproverName, operator.name())
                .set(Payroll::getApproveTime, now)
                .set(Payroll::getUpdateTime, now));
        writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                approved ? PayrollLogAction.APPROVE : PayrollLogAction.REJECT,
                payroll.getStatus(), toStatus, storedRemark, null, null, null);
        return toVO(requirePayroll(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO confirm(Long id) {
        Payroll payroll = requirePayroll(id);
        if (!Objects.equals(payroll.getEmployeeId(), UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION);
        }
        PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_CONFIRM);
        if (PayrollStatus.CONFIRMED.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "工资单已确认，无需重复确认");
        }
        if (!PayrollStatus.PUBLISHED.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "工资单尚未发布，暂不可确认");
        }
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getStatus, PayrollStatus.CONFIRMED.name())
                .set(Payroll::getConfirmTime, now)
                .set(Payroll::getUpdateTime, now));
        writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                PayrollLogAction.CONFIRM, payroll.getStatus(), PayrollStatus.CONFIRMED.name(),
                null, null, null, null);
        return toVO(requirePayroll(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO objection(Long id, String reason) {
        if (reason == null || reason.trim().length() < REASON_MIN || reason.trim().length() > REASON_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "异议原因长度须为 2-200");
        }
        Payroll payroll = requirePayroll(id);
        if (!Objects.equals(payroll.getEmployeeId(), UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION);
        }
        PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_OBJECTION);
        if (!PayrollStatus.PUBLISHED.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "仅已发布的工资单可提异议");
        }
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        String trimmed = reason.trim();
        // 退回异议态（C-1）：清空确认/发布时间与发布人（与 Mock 一致，避免时间线自相矛盾）；异议历史永久留 payroll_log
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getStatus, PayrollStatus.OBJECTED.name())
                .set(Payroll::getObjectionReason, trimmed)
                .set(Payroll::getObjectionTime, now)
                .set(Payroll::getConfirmTime, null)
                .set(Payroll::getPublishTime, null)
                .set(Payroll::getPublisherId, null)
                .set(Payroll::getPublisherName, null)
                .set(Payroll::getUpdateTime, now));
        writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                PayrollLogAction.OBJECTION, payroll.getStatus(), PayrollStatus.OBJECTED.name(),
                trimmed, null, null, null);
        Payroll objected = requirePayroll(id);
        // 落 OBJECTED 后投递类型 9（→管理员）；以落库状态为准，非法状态不投递
        if (PayrollStatus.OBJECTED.name().equals(objected.getStatus())) {
            notifySupport.notifyObjection(objected);
        }
        return toVO(objected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollVO pay(Long id, String remark) {
        Payroll payroll = requirePayroll(id);
        PayrollStateMachine.assertMutable(payroll, PayrollStateMachine.ACTION_PAY);
        // 来源须为 CONFIRMED（且员工已确认）；来源非法显式 9403，不得静默
        if (!PayrollStatus.CONFIRMED.name().equals(payroll.getStatus()) || payroll.getConfirmTime() == null) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID, "仅已确认的工资单可确认发放");
        }
        Operator operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, id)
                .set(Payroll::getStatus, PayrollStatus.PAID.name())
                .set(Payroll::getPaidById, operator.id())
                .set(Payroll::getPaidByName, operator.name())
                .set(Payroll::getPaidTime, now)
                .set(Payroll::getUpdateTime, now));
        writeLog(operator, payroll.getId(), payroll.getEmployeeId(), payroll.getMonth(),
                PayrollLogAction.PAY, payroll.getStatus(), PayrollStatus.PAID.name(),
                null, null, null, (remark == null || remark.isBlank()) ? null : remark.trim());
        return toVO(requirePayroll(id));
    }

    // ==================== 手工调整对账汇总（I-10） ====================

    @Override
    @Transactional(readOnly = true)
    public PayrollManualAdjustmentSummaryVO manualAdjustmentSummary(PayrollManualAdjustmentQuery query) {
        PayrollManualAdjustmentQuery safe = query == null ? new PayrollManualAdjustmentQuery() : query;
        String month = safe.getMonth();
        if (!notBlank(month) || !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        Long stationId = safe.getStationId();
        List<Long> stationEmployeeIds = null;
        if (stationId != null) {
            stationEmployeeIds = employeeIdsOfStation(stationId);
            if (stationEmployeeIds.isEmpty()) {
                return emptyAdjustmentSummary(month, stationId);
            }
        }
        // 以 payroll_log 冗余定位列（employee_id + month）为准汇总：覆盖重建会物理删除 DRAFT/REJECTED 单、
        // 更换 payroll_id，若按 payroll_id 关联会漏掉「已删单」上的加扣款留痕（安全补偿控制要求完整可追溯）。
        // 口径：计入 ITEM_ADD（新增加/扣款，I-6）与 ITEM_UPDATE（改既有 MANUAL 项金额，C-3）——
        //       前者按 after.items[*].itemType 归 ADDITION/DEDUCTION；后者按 after − before 差值折算净影响。
        LambdaQueryWrapper<PayrollLog> wrapper = new LambdaQueryWrapper<PayrollLog>()
                .eq(PayrollLog::getMonth, month)
                .in(PayrollLog::getAction, List.of(PayrollLogAction.ITEM_ADD, PayrollLogAction.ITEM_UPDATE));
        if (stationEmployeeIds != null) {
            wrapper.in(PayrollLog::getEmployeeId, stationEmployeeIds);
        }
        List<PayrollLog> rows = payrollLogMapper.selectList(wrapper);

        Map<Long, AdjustmentAccumulator> byEmployee = new LinkedHashMap<>();
        for (PayrollLog row : rows) {
            if (row.getEmployeeId() == null) {
                // 冗余定位列缺失（不应出现）：无法归属员工，跳过并留痕，避免污染汇总总数
                log.warn("payroll_log 缺 employee_id，对账汇总跳过：logId={}", row.getId());
                continue;
            }
            AdjustmentAccumulator acc = byEmployee.computeIfAbsent(row.getEmployeeId(),
                    key -> new AdjustmentAccumulator(key));
            if (PayrollLogAction.ITEM_UPDATE.equals(row.getAction())) {
                acc.updateCount++;
                accumulateUpdate(acc, row);
            } else {
                acc.addCount++;
                accumulateAdd(acc, row);
            }
        }

        List<Long> employeeIds = new ArrayList<>(byEmployee.keySet());
        employeeIds.sort(java.util.Comparator.naturalOrder());
        Map<Long, Employee> employeeMap = loadEmployees(employeeIds);

        PayrollManualAdjustmentEmployeeVO total = new PayrollManualAdjustmentEmployeeVO();
        total.setEmployeeName("合计");
        List<PayrollManualAdjustmentEmployeeVO> list = new ArrayList<>(employeeIds.size());
        for (Long employeeId : employeeIds) {
            Employee employee = employeeMap.get(employeeId);
            PayrollManualAdjustmentEmployeeVO vo = byEmployee.get(employeeId).toVO(employee);
            list.add(vo);
            total.setAdditionCount(total.getAdditionCount() + vo.getAdditionCount());
            total.setAdditionTotal(total.getAdditionTotal().add(vo.getAdditionTotal()));
            total.setDeductionCount(total.getDeductionCount() + vo.getDeductionCount());
            total.setDeductionTotal(total.getDeductionTotal().add(vo.getDeductionTotal()));
            total.setAddCount(total.getAddCount() + vo.getAddCount());
            total.setUpdateCount(total.getUpdateCount() + vo.getUpdateCount());
            total.setUpdateIncreaseTotal(total.getUpdateIncreaseTotal().add(vo.getUpdateIncreaseTotal()));
            total.setUpdateDecreaseTotal(total.getUpdateDecreaseTotal().add(vo.getUpdateDecreaseTotal()));
        }
        total.setNetImpact(total.getAdditionTotal().subtract(total.getDeductionTotal()));
        total.setTotalNetImpact(total.getNetImpact().add(total.getUpdateIncreaseTotal())
                .subtract(total.getUpdateDecreaseTotal()));

        PayrollManualAdjustmentSummaryVO summary = new PayrollManualAdjustmentSummaryVO();
        summary.setMonth(month);
        summary.setStationId(stationId);
        summary.setList(list);
        summary.setTotal(total);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordAutoSubmitSkipped(Long payrollId, String reason) {
        if (payrollId == null) {
            return;
        }
        Payroll payroll = payrollMapper.selectById(payrollId);
        if (payroll == null) {
            log.warn("自动提交跳过留痕无法落库：工资单不存在，payrollId={}", payrollId);
            return;
        }
        // 为何不走 writeLog：自动路径主体为 SYSTEM，而 writeLog 恒写 USER；此处显式置 SYSTEM（不留操作人快照）。
        PayrollLog row = new PayrollLog();
        row.setPayrollId(payroll.getId());
        row.setEmployeeId(payroll.getEmployeeId());
        row.setMonth(payroll.getMonth());
        row.setAction(PayrollLogAction.AUTO_SUBMIT_SKIPPED);
        row.setOperatorType(PayrollLogAction.OPERATOR_SYSTEM);
        row.setTime(LocalDateTime.now());
        row.setFromStatus(payroll.getStatus());
        row.setToStatus(payroll.getStatus());
        row.setReason(truncate(reason, REASON_MAX));
        row.setRemark("自动算薪提交审核未执行：单据保持草稿，可由管理员手工提交");
        payrollLogMapper.insert(row);
    }

    /** ITEM_ADD：按 {@code after.items[*].itemType} 归加款/扣款，金额取 {@code amount} */
    private void accumulateAdd(AdjustmentAccumulator acc, PayrollLog row) {
        for (Map<String, Object> item : extractItems(row.getAfter())) {
            BigDecimal amount = coerceAmount(item.get("amount"));
            if (amount == null) {
                continue;
            }
            String itemType = item.get("itemType") == null ? null : String.valueOf(item.get("itemType"));
            if (PayrollItemType.ADDITION.name().equals(itemType)) {
                acc.additionCount++;
                acc.additionTotal = acc.additionTotal.add(amount);
            } else if (PayrollItemType.DEDUCTION.name().equals(itemType)) {
                acc.deductionCount++;
                acc.deductionTotal = acc.deductionTotal.add(amount);
            }
        }
    }

    /**
     * ITEM_UPDATE：逐项以 {@code after − before} 计金额变动（净影响 = 变动后 − 变动前），
     * 折算为对实发的方向（加款项增额 → 实发增；扣款项增额 → 实发减），再按方向拆分。
     */
    private void accumulateUpdate(AdjustmentAccumulator acc, PayrollLog row) {
        Map<String, BigDecimal> beforeByKey = indexItemsByKey(row.getBefore());
        for (Map<String, Object> item : extractItems(row.getAfter())) {
            Object key = item.get("itemKey");
            BigDecimal afterAmount = coerceAmount(item.get("amount"));
            BigDecimal beforeAmount = key == null ? null : beforeByKey.get(String.valueOf(key));
            if (afterAmount == null || beforeAmount == null) {
                // 无对应前值（快照缺失 / 误记为 UPDATE 的新增项）：无法计差，跳过以免污染方向合计
                continue;
            }
            String itemType = item.get("itemType") == null ? null : String.valueOf(item.get("itemType"));
            BigDecimal delta = afterAmount.subtract(beforeAmount);
            BigDecimal impact;
            if (PayrollItemType.ADDITION.name().equals(itemType)) {
                impact = delta;
            } else if (PayrollItemType.DEDUCTION.name().equals(itemType)) {
                impact = delta.negate();
            } else {
                continue;
            }
            if (impact.signum() > 0) {
                acc.updateIncreaseTotal = acc.updateIncreaseTotal.add(impact);
            } else if (impact.signum() < 0) {
                acc.updateDecreaseTotal = acc.updateDecreaseTotal.add(impact.negate());
            }
        }
    }

    /** 明细级快照按 {@code itemKey} 归集金额（供 ITEM_UPDATE 前后差值配对） */
    private Map<String, BigDecimal> indexItemsByKey(String snapshotJson) {
        Map<String, BigDecimal> byKey = new HashMap<>();
        for (Map<String, Object> item : extractItems(snapshotJson)) {
            Object key = item.get("itemKey");
            BigDecimal amount = coerceAmount(item.get("amount"));
            if (key != null && amount != null) {
                byKey.put(String.valueOf(key), amount);
            }
        }
        return byKey;
    }

    private PayrollManualAdjustmentSummaryVO emptyAdjustmentSummary(String month, Long stationId) {
        PayrollManualAdjustmentSummaryVO summary = new PayrollManualAdjustmentSummaryVO();
        summary.setMonth(month);
        summary.setStationId(stationId);
        summary.setList(List.of());
        PayrollManualAdjustmentEmployeeVO total = new PayrollManualAdjustmentEmployeeVO();
        total.setEmployeeName("合计");
        summary.setTotal(total);
        return summary;
    }

    /** 取驿站下员工 id（员工归属口径）；空集表示该站暂无员工 */
    private List<Long> employeeIdsOfStation(Long stationId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId).eq(Employee::getStationId, stationId);
        List<Long> ids = new ArrayList<>();
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            if (employee.getId() != null) {
                ids.add(employee.getId());
            }
        }
        return ids;
    }

    /** 从留痕快照取手工项列表（白名单键 items[{itemKey,itemType,itemName,amount}]；before / after 通用） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(String snapshotJson) {
        Object parsed = parseJson(snapshotJson);
        if (!(parsed instanceof Map<?, ?> snapshot)) {
            return List.of();
        }
        Object items = snapshot.get("items");
        if (!(items instanceof List<?> itemList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : itemList) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
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
                command.getOffboardingId(), command.getRemark(), PayrollStatus.DRAFT.name(), List.of());
        return new PayrollSettlementRef(created.getId(), created.getPayrollNo(), created.getNetAmount());
    }

    // ==================== 私有：算薪内核 ====================

    /**
     * 建单：解析明细 → 追加保留项 → 合计 → 落库（工资单 + 明细）；返回已落库实体。
     *
     * @param preservedItems 覆盖重建时保留的既有 MANUAL 项（接续在规则项之后；新建单传空列表）
     */
    private Payroll createPayroll(Employee employee, String month, String billType, PayrollRule rule,
                                  List<PayrollRuleItem> enabledItems, String snapshot,
                                  Long offboardingId, String remark, String status,
                                  List<PayrollItemDraft> preservedItems) {
        PayrollCalcContext ctx = contextProvider.contextOf(employee.getId(), month);
        List<PayrollItemDraft> drafts = new ArrayList<>(resolveItems(enabledItems, ctx));
        if (preservedItems != null) {
            drafts.addAll(preservedItems);
        }
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

    /** 按当前明细行（含改动/新增后）重算四项合计（复用 PayrollTotalsPolicy，口径唯一） */
    private PayrollTotals recalcTotals(List<PayrollItem> rows) {
        List<PayrollItemDraft> drafts = new ArrayList<>(rows.size());
        for (PayrollItem row : rows) {
            drafts.add(new PayrollItemDraft(row.getItemKey(), row.getItemName(), row.getItemType(),
                    row.getSource(), row.getAmount(), row.getDetail()));
        }
        return PayrollTotalsPolicy.of(drafts, algoProperties.getPayroll().isAllowNegativeNet());
    }

    /**
     * 回写四项合计并同步内存态（避免为取最新合计多查一次库）。
     * <p>为什么就地改内存：调用方随后要用最新合计写出参与留痕快照，重新查库既多余又依赖读一致性。
     */
    private Payroll applyTotals(Payroll payroll, PayrollTotals totals) {
        LocalDateTime now = LocalDateTime.now();
        payrollMapper.update(null, new LambdaUpdateWrapper<Payroll>()
                .eq(Payroll::getId, payroll.getId())
                .set(Payroll::getAdditionTotal, totals.additionTotal())
                .set(Payroll::getDeductionTotal, totals.deductionTotal())
                .set(Payroll::getGrossAmount, totals.grossAmount())
                .set(Payroll::getNetAmount, totals.netAmount())
                .set(Payroll::getUpdateTime, now));
        payroll.setAdditionTotal(totals.additionTotal());
        payroll.setDeductionTotal(totals.deductionTotal());
        payroll.setGrossAmount(totals.grossAmount());
        payroll.setNetAmount(totals.netAmount());
        payroll.setUpdateTime(now);
        return payroll;
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
     * 覆盖重建：<b>物理</b>删除目标旧单及其明细，供重复生成幂等。
     * <p>
     * 为什么不用 {@code payrollMapper.delete(wrapper)}：{@link Payroll} 标注 {@code @TableLogic}
     * 且 application.yml 全局开启逻辑删除，该调用只把旧行 {@code is_deleted} 置 1，物理行仍在，
     * 重复生成只增不减（本缺陷根因：同账期连跑两次 → 物理行 63 → 126）。故改走自定义物理删除。
     * <p>
     * 范围收口：只清「可覆盖」状态（DRAFT/REJECTED，口径见 {@link PayrollGenerateGuard}），
     * 已提交/已发布单据不取不删（9405 语义不变）；同范围的历史逻辑删除残留行一并清除，
     * 使重跑后 {@code COUNT(*) == COUNT(DISTINCT employee_id)}。删除顺序：先明细后主单，杜绝孤儿。
     */
    private void deleteExisting(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先删明细再删主单：payroll_item 无物理外键，靠顺序与范围保证不留孤儿
        payrollItemMapper.deletePhysicallyByPayrollIds(ids);
        payrollMapper.deletePhysicallyByIds(ids);
    }

    /** 目标员工所属驿站集合（覆盖重建的 9405 收敛维度） */
    private Set<Long> targetStations(List<Employee> employees) {
        Set<Long> stations = new HashSet<>();
        for (Employee employee : employees) {
            if (employee.getStationId() != null) {
                stations.add(employee.getStationId());
            }
        }
        return stations;
    }

    /** 同月同类型单按员工建索引（取首条；用于覆盖重建前的旧单快照） */
    private Map<Long, Payroll> indexByEmployee(List<Payroll> rows) {
        Map<Long, Payroll> map = new HashMap<>();
        for (Payroll row : rows) {
            map.putIfAbsent(row.getEmployeeId(), row);
        }
        return map;
    }

    private Set<String> ruleItemKeys(List<PayrollRuleItem> enabledItems) {
        Set<String> keys = new HashSet<>();
        for (PayrollRuleItem item : enabledItems) {
            if (item.getItemKey() != null) {
                keys.add(item.getItemKey());
            }
        }
        return keys;
    }

    /** 覆盖重建前快照既有 MANUAL 明细（仅取活跃旧单，按原 id 升序保持相对次序） */
    private List<PayrollItemDraft> loadPreservedManual(Payroll existing) {
        if (existing == null || existing.getId() == null) {
            return List.of();
        }
        List<PayrollItem> rows = payrollItemMapper.selectList(new LambdaQueryWrapper<PayrollItem>()
                .eq(PayrollItem::getPayrollId, existing.getId())
                .eq(PayrollItem::getSource, PayrollSource.MANUAL.name())
                .orderByAsc(PayrollItem::getId));
        List<PayrollItemDraft> drafts = new ArrayList<>(rows.size());
        for (PayrollItem row : rows) {
            drafts.add(new PayrollItemDraft(row.getItemKey(), row.getItemName(), row.getItemType(),
                    row.getSource(), row.getAmount(), row.getDetail()));
        }
        return drafts;
    }

    /** 生成 MANUAL_<账期>_<序号> 且不与既有 key 冲突的新 item_key（U-09） */
    private String nextManualKey(String month, Set<String> usedKeys) {
        String monthPart = month == null ? "NA" : month.replace("-", "");
        String prefix = MANUAL_KEY_PREFIX + monthPart + "_";
        int seq = 1;
        while (usedKeys.contains(prefix + seq)) {
            seq++;
        }
        return prefix + seq;
    }

    private int nextSortOrder(List<PayrollItem> rows) {
        int max = 0;
        for (PayrollItem row : rows) {
            if (row.getSortOrder() != null && row.getSortOrder() > max) {
                max = row.getSortOrder();
            }
        }
        return max + 1;
    }

    // ==================== 私有：状态守卫 / 操作人 ====================

    private Payroll requirePayroll(Long id) {
        Payroll payroll = id == null ? null : payrollMapper.selectById(id);
        if (payroll == null) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_NOT_EXISTS);
        }
        return payroll;
    }

    private BusinessException statusInvalid(Payroll payroll) {
        return new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                "当前状态（" + PayrollStatus.labelOf(payroll.getStatus()) + "）不允许该操作");
    }

    /** 事由必填校验（2-200 字）；缺失/越界 → 9412（M-3/REG-03） */
    private String requireReason(String reason) {
        String trimmed = reason == null ? null : reason.trim();
        if (trimmed == null || trimmed.length() < REASON_MIN || trimmed.length() > REASON_MAX) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_REASON_REQUIRED);
        }
        return trimmed;
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
        return new Operator(id, name, user == null ? null : user.getRole());
    }

    // ==================== 私有：留痕 ====================

    /**
     * 写 1 条工资单留痕（追加型，永不更新/删除）。
     * <p>{@code before}/{@code after} 仅写 §3.2 白名单键（明细级 + 合计级），禁止 dump 实体 / {@code rule_snapshot}。
     */
    private void writeLog(Operator operator, Long payrollId, Long employeeId, String month, String action,
                          String fromStatus, String toStatus, String reason,
                          Object before, Object after, String remark) {
        PayrollLog row = new PayrollLog();
        row.setPayrollId(payrollId);
        row.setEmployeeId(employeeId);
        row.setMonth(month);
        row.setAction(action);
        row.setOperatorId(operator.id());
        row.setOperatorName(operator.name());
        row.setOperatorRole(operator.role());
        row.setOperatorType(PayrollLogAction.OPERATOR_USER);
        row.setTime(LocalDateTime.now());
        row.setFromStatus(fromStatus);
        row.setToStatus(toStatus);
        row.setReason(reason);
        row.setBefore(JsonUtil.write(before));
        row.setAfter(JsonUtil.write(after));
        row.setRemark(remark);
        payrollLogMapper.insert(row);
    }

    private PayrollLogVO toLogVO(PayrollLog row, boolean admin) {
        PayrollLogVO vo = new PayrollLogVO();
        vo.setId(row.getId());
        vo.setAction(row.getAction());
        vo.setTime(row.getTime());
        vo.setReason(row.getReason());
        vo.setToStatus(row.getToStatus());
        if (admin) {
            vo.setOperatorId(row.getOperatorId());
            vo.setOperatorName(row.getOperatorName());
            vo.setOperatorRole(row.getOperatorRole());
            vo.setOperatorType(row.getOperatorType());
            vo.setFromStatus(row.getFromStatus());
            vo.setBefore(parseJson(row.getBefore()));
            vo.setAfter(parseJson(row.getAfter()));
            vo.setRemark(row.getRemark());
        }
        return vo;
    }

    /** 合计级快照（白名单键，脱敏：不含 rule_snapshot / 个人信息） */
    private Map<String, Object> totalsSnapshot(Payroll payroll) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("additionTotal", payroll.getAdditionTotal());
        snapshot.put("deductionTotal", payroll.getDeductionTotal());
        snapshot.put("grossAmount", payroll.getGrossAmount());
        snapshot.put("netAmount", payroll.getNetAmount());
        return snapshot;
    }

    /** 合计 + 变动项快照：由 before→after 可唯一重建本次 netAmount 差值与变动项 */
    private Map<String, Object> adjustSnapshot(Payroll payroll, List<Map<String, Object>> items) {
        Map<String, Object> snapshot = new LinkedHashMap<>(totalsSnapshot(payroll));
        snapshot.put("items", items);
        return snapshot;
    }

    /** 明细级快照（白名单键：itemKey/itemType/itemName/amount） */
    private Map<String, Object> itemSnapshot(PayrollItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("itemKey", item.getItemKey());
        snapshot.put("itemType", item.getItemType());
        snapshot.put("itemName", item.getItemName());
        snapshot.put("amount", item.getAmount());
        return snapshot;
    }

    /** 生成留痕快照：合计 + manualKept 数量 + 保留项摘要 */
    private Map<String, Object> generateSnapshot(Payroll payroll, List<PayrollItemDraft> preserved) {
        Map<String, Object> snapshot = new LinkedHashMap<>(totalsSnapshot(payroll));
        snapshot.put("manualKept", preserved == null ? 0 : preserved.size());
        if (preserved != null && !preserved.isEmpty()) {
            List<Map<String, Object>> kept = new ArrayList<>(preserved.size());
            for (PayrollItemDraft draft : preserved) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemKey", draft.key());
                item.put("itemType", draft.type());
                item.put("amount", draft.amount());
                kept.add(item);
            }
            snapshot.put("manualItems", kept);
        }
        return snapshot;
    }

    private Object parseJson(String json) {
        return JsonUtil.read(json, new TypeReference<Object>() {
        });
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
        vo.setPaidById(payroll.getPaidById());
        vo.setPaidByName(payroll.getPaidByName());
        vo.setPaidTime(payroll.getPaidTime());
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

    /** 按字符安全截断（防超出 VARCHAR(N) 长度导致落库失败） */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 对账汇总累加器（可变，仅汇总期间使用） */
    private static final class AdjustmentAccumulator {
        private final Long employeeId;
        /** —— ITEM_ADD（新增加/扣款）口径，保持既有字段语义 —— */
        private int additionCount;
        private BigDecimal additionTotal = BigDecimal.ZERO;
        private int deductionCount;
        private BigDecimal deductionTotal = BigDecimal.ZERO;
        /** —— 动作级笔数 —— */
        private int addCount;
        private int updateCount;
        /** —— ITEM_UPDATE（改金额）方向净影响 —— */
        private BigDecimal updateIncreaseTotal = BigDecimal.ZERO;
        private BigDecimal updateDecreaseTotal = BigDecimal.ZERO;

        private AdjustmentAccumulator(Long employeeId) {
            this.employeeId = employeeId;
        }

        private PayrollManualAdjustmentEmployeeVO toVO(Employee employee) {
            BigDecimal netImpact = additionTotal.subtract(deductionTotal);
            PayrollManualAdjustmentEmployeeVO vo = new PayrollManualAdjustmentEmployeeVO();
            vo.setEmployeeId(employeeId);
            vo.setEmployeeName(employee == null ? null : employee.getRealName());
            vo.setAdditionCount(additionCount);
            vo.setAdditionTotal(additionTotal);
            vo.setDeductionCount(deductionCount);
            vo.setDeductionTotal(deductionTotal);
            vo.setNetImpact(netImpact);
            vo.setAddCount(addCount);
            vo.setUpdateCount(updateCount);
            vo.setUpdateIncreaseTotal(updateIncreaseTotal);
            vo.setUpdateDecreaseTotal(updateDecreaseTotal);
            vo.setTotalNetImpact(netImpact.add(updateIncreaseTotal).subtract(updateDecreaseTotal));
            return vo;
        }
    }

    /** 操作人（id + 姓名快照 + 角色快照；姓名取当前登录员工 real_name，取不到回落用户名） */
    private record Operator(Long id, String name, String role) {
    }
}
