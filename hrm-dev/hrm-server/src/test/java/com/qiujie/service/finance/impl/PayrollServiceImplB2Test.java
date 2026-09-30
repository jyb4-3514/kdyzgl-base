package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.dto.finance.PayrollItemAddRequest;
import com.qiujie.dto.finance.PayrollItemAdjustRequest;
import com.qiujie.dto.finance.PayrollPublishRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollItem;
import com.qiujie.entity.PayrollLog;
import com.qiujie.entity.PayrollRule;
import com.qiujie.entity.PayrollRuleItem;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.PayrollItemMapper;
import com.qiujie.mapper.PayrollLogMapper;
import com.qiujie.mapper.PayrollMapper;
import com.qiujie.mapper.PayrollRuleItemMapper;
import com.qiujie.mapper.PayrollRuleMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.finance.support.PayrollCalcContext;
import com.qiujie.service.finance.support.PayrollItemAmount;
import com.qiujie.service.finance.support.PayrollNotifySupport;
import com.qiujie.service.finance.support.PayrollResolverRegistry;
import com.qiujie.util.UserContext;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollLogVO;
import com.qiujie.vo.finance.PayrollVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B2 批次服务层单测（Mockito，无需 DB）：状态机扩展后的 I-6 手工加扣款 / I-7 留痕裁剪 / I-8 发放归档 /
 * PAID 冻结（9413）/ C-7 覆盖重建保留 MANUAL 明细与按驿站收敛 9405。
 * <p>
 * 与 {@code PayrollServiceImplGenerateIdempotencyTest} 同一手法：内存仓库替身 + Mockito 打桩；
 * 判据纯逻辑部分另见 {@code PayrollStateMachineTest} / {@code PayrollGenerateLockPolicyTest}。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollServiceImplB2Test {

    private static final Long PAYROLL_ID = 500L;
    private static final Long EMPLOYEE_ID = 11L;
    private static final Long STATION_ID = 8L;
    private static final String MONTH = "2026-09";
    private static final Long RULE_ID = 1L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Payroll.class);
        TableInfoHelper.initTableInfo(assistant, PayrollItem.class);
        TableInfoHelper.initTableInfo(assistant, PayrollLog.class);
        TableInfoHelper.initTableInfo(assistant, Employee.class);
        TableInfoHelper.initTableInfo(assistant, Station.class);
        TableInfoHelper.initTableInfo(assistant, PayrollRule.class);
        TableInfoHelper.initTableInfo(assistant, PayrollRuleItem.class);
    }

    private PayrollMapper payrollMapper;
    private PayrollItemMapper payrollItemMapper;
    private PayrollLogMapper payrollLogMapper;
    private PayrollRuleMapper payrollRuleMapper;
    private PayrollRuleItemMapper payrollRuleItemMapper;
    private EmployeeMapper employeeMapper;
    private StationMapper stationMapper;
    private PayrollContextProvider contextProvider;
    private PayrollResolverRegistry resolverRegistry;

    private PayrollServiceImpl service;

    /** 明细仓库（selectList 返回其副本、insert 落入本仓库，二者配合使新增明细可观测） */
    private final List<PayrollItem> itemStore = new ArrayList<>();
    /** 工资单仓库 */
    private final List<Payroll> payrollStore = new ArrayList<>();
    /** 留痕捕获 */
    private final List<PayrollLog> logStore = new ArrayList<>();
    /** update 包装器捕获（用于断言写回列，Mockito 替身无法真读库） */
    private final List<Object> updateWrappers = new ArrayList<>();
    private long payrollSeq = 500L;
    private long itemSeq = 1000L;

    @BeforeEach
    void setUp() {
        itemStore.clear();
        payrollStore.clear();
        logStore.clear();
        updateWrappers.clear();
        payrollSeq = 500L;
        itemSeq = 1000L;

        payrollMapper = mock(PayrollMapper.class);
        payrollItemMapper = mock(PayrollItemMapper.class);
        payrollLogMapper = mock(PayrollLogMapper.class);
        payrollRuleMapper = mock(PayrollRuleMapper.class);
        payrollRuleItemMapper = mock(PayrollRuleItemMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        stationMapper = mock(StationMapper.class);
        contextProvider = mock(PayrollContextProvider.class);
        resolverRegistry = mock(PayrollResolverRegistry.class);
        AlgoProperties algoProperties = new AlgoProperties();

        service = new PayrollServiceImpl(payrollMapper, payrollItemMapper, payrollRuleMapper,
                payrollRuleItemMapper, employeeMapper, stationMapper, contextProvider, resolverRegistry,
                algoProperties, payrollLogMapper, mock(PayrollNotifySupport.class));

        when(employeeMapper.selectById(anyLong())).thenAnswer(inv -> employee(inv.getArgument(0), "张三"));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(EMPLOYEE_ID, "张三")));
        when(stationMapper.selectList(any())).thenReturn(List.of(station()));
        // selectList 返回副本（代码会用 rows.add 同步内存态），insert 落到仓库，两者配合可观测
        when(payrollItemMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(itemStore));
        when(payrollItemMapper.insert(any(PayrollItem.class))).thenAnswer(inv -> {
            PayrollItem row = inv.getArgument(0);
            row.setId(++itemSeq);
            row.setIsDeleted(0);
            itemStore.add(row);
            return 1;
        });
        when(payrollItemMapper.updateById(any(PayrollItem.class))).thenReturn(1);
        when(payrollMapper.update(any(), any())).thenAnswer(inv -> {
            updateWrappers.add(inv.getArgument(1));
            return 1;
        });
        when(payrollMapper.insert(any(Payroll.class))).thenAnswer(inv -> {
            Payroll row = inv.getArgument(0);
            row.setId(++payrollSeq);
            row.setIsDeleted(0);
            payrollStore.add(row);
            return 1;
        });
        when(payrollLogMapper.insert(any(PayrollLog.class))).thenAnswer(inv -> {
            logStore.add(inv.getArgument(0));
            return 1;
        });
        when(contextProvider.contextOf(anyLong(), anyString())).thenReturn(PayrollCalcContext.empty());
        when(resolverRegistry.resolve(anyString(), any(), any())).thenAnswer(inv -> {
            String source = inv.getArgument(0);
            return new PayrollItemAmount("FIXED".equals(source)
                    ? new BigDecimal("5000.00") : new BigDecimal("100.00"), "规则计算");
        });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== I-6 手工加扣款 ====================

    @Test
    @DisplayName("I-6：事由缺失 / 过短 / 过长 → 9412")
    void addItemReasonRequired9412() {
        for (String reason : new String[]{null, "", "a", repeat('x', 201)}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.addItem(PAYROLL_ID, addRequest("ADDITION", "加班补贴", "100", reason)));
            assertEquals(ErrorCode.FINANCE_PAYROLL_REASON_REQUIRED.getCode(), ex.getCode(), "事由非法应回 9412");
        }
    }

    @Test
    @DisplayName("I-6：item_key 重复 → 9411（服务端生成后二次校验）")
    void addItemDuplicateKey9411() {
        seedPayroll("DRAFT");
        when(payrollItemMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addItem(PAYROLL_ID, addRequest("ADDITION", "加班补贴", "100", "9 月加班")));
        assertEquals(ErrorCode.FINANCE_PAYROLL_ITEM_EXISTS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("I-6：加款计入应发、扣款计入扣项；重算四项合计；MANUAL_ 前缀 key；写留痕")
    void addItemRecalculatesTotals() {
        seedPayroll("DRAFT");
        seedItem("BASE", "基本工资", "ADDITION", "FIXED", "5000.00", 1);
        seedItem("FINE", "罚款", "DEDUCTION", "ATTENDANCE", "100.00", 2);
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        PayrollVO afterAddition = service.addItem(PAYROLL_ID,
                addRequest("ADDITION", "加班补贴", "200", "9 月加班 4 次"));
        assertEquals(new BigDecimal("5200.00"), afterAddition.getAdditionTotal());
        assertEquals(new BigDecimal("100.00"), afterAddition.getDeductionTotal());
        assertEquals(new BigDecimal("5200.00"), afterAddition.getGrossAmount());
        assertEquals(new BigDecimal("5100.00"), afterAddition.getNetAmount());

        PayrollItem added = itemStore.get(itemStore.size() - 1);
        assertEquals("MANUAL_202609_1", added.getItemKey(), "key 须服务端生成且带 MANUAL_ 前缀");
        assertEquals("MANUAL", added.getSource());
        assertEquals("ADDITION", added.getItemType());
        assertEquals(new BigDecimal("200"), added.getAmount());
        assertEquals(3, added.getSortOrder().intValue(), "接续在既有明细之后");

        PayrollVO afterDeduction = service.addItem(PAYROLL_ID,
                addRequest("DEDUCTION", "设备赔偿", "50", "9 月扫码枪损坏"));
        assertEquals(new BigDecimal("5200.00"), afterDeduction.getAdditionTotal());
        assertEquals(new BigDecimal("150.00"), afterDeduction.getDeductionTotal());
        assertEquals(new BigDecimal("5050.00"), afterDeduction.getNetAmount());
        assertEquals("MANUAL_202609_2", itemStore.get(itemStore.size() - 1).getItemKey(),
                "第二笔自动递增序号");

        assertEquals(2, logStore.size(), "每笔加扣款各写 1 条留痕");
        assertEquals("ITEM_ADD", logStore.get(0).getAction());
        assertEquals("9 月加班 4 次", logStore.get(0).getReason());
        assertEquals(EMPLOYEE_ID, logStore.get(0).getEmployeeId());
        assertEquals(MONTH, logStore.get(0).getMonth());
        assertNotNull(logStore.get(0).getAfter(), "after 须含合计快照");
    }

    @Test
    @DisplayName("I-6：itemType / itemName / 金额非法 → 400")
    void addItemInvalidParams() {
        seedPayroll("DRAFT");
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(),
                assertThrows(BusinessException.class, () -> service.addItem(PAYROLL_ID,
                        addRequest("BONUS", "加班补贴", "100", "9 月加班"))).getCode());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(),
                assertThrows(BusinessException.class, () -> service.addItem(PAYROLL_ID,
                        addRequest("ADDITION", " ", "100", "9 月加班"))).getCode());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(),
                assertThrows(BusinessException.class, () -> service.addItem(PAYROLL_ID,
                        addRequest("ADDITION", "加班补贴", "0", "9 月加班"))).getCode());
    }

    // ==================== PAID 冻结（9413，统一收口） ====================

    @Test
    @DisplayName("PAID 冻结：全部写入口一律 9413（改明细/加扣款/submit/approve/publish/confirm/objection/pay）")
    void paidFrozen9413AcrossWriteEntries() {
        seedPayroll("PAID");
        seedItem("BASE", "基本工资", "ADDITION", "FIXED", "5000.00", 1);
        // 员工本人（confirm / objection 需归属校验通过后再命中冻结守卫）
        UserContext.set(new LoginUser(EMPLOYEE_ID, "张三", "STAFF", "jti", String.valueOf(STATION_ID)));

        List<PayrollItemAdjustRequest> adjust = List.of(adjust("BASE", "1"));
        assert9413(() -> service.updateItems(PAYROLL_ID, "调整理由", adjust));
        assert9413(() -> service.addItem(PAYROLL_ID, addRequest("ADDITION", "补贴", "1", "事由一二")));
        assert9413(() -> service.submit(List.of(PAYROLL_ID)));
        assert9413(() -> service.approve(PAYROLL_ID, true, null));
        PayrollPublishRequest publish = new PayrollPublishRequest();
        publish.setIds(List.of(PAYROLL_ID));
        assert9413(() -> service.publish(publish));
        assert9413(() -> service.confirm(PAYROLL_ID));
        assert9413(() -> service.objection(PAYROLL_ID, "金额有误"));
        assert9413(() -> service.pay(PAYROLL_ID, null));
    }

    @Test
    @DisplayName("I-8：来源非 CONFIRMED → 9403；PAID 后再 pay → 9413")
    void payRequiresConfirmed() {
        seedPayroll("PUBLISHED");
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));
        assertEquals(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> service.pay(PAYROLL_ID, null)).getCode());
    }

    @Test
    @DisplayName("I-8：CONFIRMED → PAID，写回 paid_* 并写 PAY 留痕")
    void payConfirmedToPaid() {
        Payroll payroll = seedPayroll("CONFIRMED");
        payroll.setConfirmTime(java.time.LocalDateTime.now());
        seedItem("BASE", "基本工资", "ADDITION", "FIXED", "5000.00", 1);
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        service.pay(PAYROLL_ID, "已打款");

        // 替身无法真读库：以写回 SQL 的 SET 列 + 留痕证明「当前态 + 事件流」双写
        String sqlSet = lastUpdateSqlSet();
        assertTrue(sqlSet.contains("paid_by_id"), "须写回 paid_by_id");
        assertTrue(sqlSet.contains("paid_by_name"));
        assertTrue(sqlSet.contains("paid_time"));

        assertEquals(1, logStore.size());
        assertEquals("PAY", logStore.get(0).getAction());
        assertEquals("CONFIRMED", logStore.get(0).getFromStatus());
        assertEquals("PAID", logStore.get(0).getToStatus());
        assertEquals("已打款", logStore.get(0).getRemark());
    }

    // ==================== C-7 生成：保留 MANUAL + 驿站收敛 ====================

    @Test
    @DisplayName("C-7：覆盖重建保留既有 source=MANUAL 明细并重算合计，写 GENERATE_MANUAL 留痕")
    void generateKeepsManualItems() {
        Payroll old = seedPayroll("DRAFT");
        seedItem("BASE", "基本工资", "ADDITION", "FIXED", "5000.00", 1);
        seedItem("FINE", "罚款", "DEDUCTION", "ATTENDANCE", "100.00", 2);
        PayrollItem manual = new PayrollItem();
        manual.setId(++itemSeq);
        manual.setPayrollId(old.getId());
        manual.setItemKey("MANUAL_202609_1");
        manual.setItemName("加班补贴");
        manual.setItemType("ADDITION");
        manual.setSource("MANUAL");
        manual.setAmount(new BigDecimal("200.00"));
        manual.setDetail("事由：9 月加班");
        manual.setSortOrder(3);
        manual.setIsDeleted(0);
        itemStore.add(manual);

        when(payrollRuleMapper.selectById(RULE_ID)).thenReturn(rule());
        when(payrollRuleItemMapper.selectList(any())).thenReturn(ruleItems());
        when(payrollMapper.selectList(any())).thenReturn(new ArrayList<>(payrollStore));
        when(payrollMapper.selectById(anyLong())).thenAnswer(inv -> findPayroll(inv.getArgument(0)));
        when(payrollMapper.selectRebuildTargetIds(anyLong(), anyString(), anyString(), anyList()))
                .thenAnswer(inv -> List.of(old.getId()));
        when(payrollItemMapper.selectList(any())).thenReturn(manualOnly());
        when(payrollItemMapper.deletePhysicallyByPayrollIds(anyList())).thenReturn(1);
        when(payrollMapper.deletePhysicallyByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            payrollStore.removeIf(row -> ids.contains(row.getId()));
            itemStore.removeIf(row -> ids.contains(row.getPayrollId()));
            return ids.size();
        });
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setMonth(MONTH);
        request.setRuleId(RULE_ID);
        PayrollGenerateVO vo = service.generate(request);

        assertEquals(1, vo.getCreated());
        Payroll created = findPayroll(vo.getPayrollIds().get(0));
        assertNotNull(created);
        // 规则项（ADDITION 5000 / DEDUCTION 100）+ 保留手工项（ADDITION 200）
        assertEquals(new BigDecimal("5200.00"), created.getAdditionTotal());
        assertEquals(new BigDecimal("100.00"), created.getDeductionTotal());
        assertEquals(new BigDecimal("5100.00"), created.getNetAmount());

        List<PayrollItem> kept = itemsOf(created.getId());
        assertEquals(3, kept.size(), "规则项 + 保留手工项");
        PayrollItem preserved = kept.get(2);
        assertEquals("MANUAL_202609_1", preserved.getItemKey());
        assertEquals("MANUAL", preserved.getSource());
        assertEquals(new BigDecimal("200.00"), preserved.getAmount());
        assertEquals(3, preserved.getSortOrder().intValue(), "保留项接续在规则项之后");

        assertEquals(1, logStore.size());
        assertEquals("GENERATE_MANUAL", logStore.get(0).getAction());
        assertTrue(logStore.get(0).getAfter().contains("manualKept"), "after 须含 manualKept");
    }

    @Test
    @DisplayName("C-7：手工 generate（I-3）保持原行为——落 DRAFT 且不自动提交（无 SUBMIT 留痕）")
    void generate_manualPathStaysDraft() {
        when(payrollRuleMapper.selectById(RULE_ID)).thenReturn(rule());
        when(payrollRuleItemMapper.selectList(any())).thenReturn(ruleItems());
        when(payrollMapper.selectList(any())).thenReturn(List.of());
        when(payrollMapper.selectRebuildTargetIds(anyLong(), anyString(), anyString(), anyList())).thenReturn(List.of());
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setMonth(MONTH);
        request.setRuleId(RULE_ID);
        PayrollGenerateVO vo = service.generate(request);

        Payroll created = findPayroll(vo.getPayrollIds().get(0));
        assertEquals("DRAFT", created.getStatus(), "手工路径须保持 DRAFT，自动提交只作用于调度/触发运行路径");
        assertTrue(logStore.stream().noneMatch(row -> "SUBMIT".equals(row.getAction())),
                "手工生成不得产生 SUBMIT 留痕");
    }

    @Test
    @DisplayName("submit：DRAFT → PENDING_APPROVAL 并写 SUBMIT 留痕（自动提交复用的既有口径）")
    void submitDraftToPendingApproval() {
        seedPayroll("DRAFT");
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        service.submit(List.of(PAYROLL_ID));

        assertTrue(lastUpdateSqlSet().contains("status"), "submit 须写回 status 列");
        assertEquals(1, logStore.size());
        assertEquals("SUBMIT", logStore.get(0).getAction());
        assertEquals("DRAFT", logStore.get(0).getFromStatus());
        assertEquals("PENDING_APPROVAL", logStore.get(0).getToStatus());
    }

    @Test
    @DisplayName("C-7：9405 按驿站收敛——他站非可覆盖单不阻断本站生成")
    void generateBlockingScopedByStation() {
        // 站 8 存在一张 PENDING_APPROVAL（不可覆盖）单；目标员工在站 9 → 不阻断
        Payroll other = new Payroll();
        other.setId(++payrollSeq);
        other.setPayrollNo("PAY-202609-99");
        other.setEmployeeId(99L);
        other.setStationId(8L);
        other.setMonth(MONTH);
        other.setBillType("MONTHLY");
        other.setStatus("PENDING_APPROVAL");
        other.setIsDeleted(0);

        when(payrollRuleMapper.selectById(RULE_ID)).thenReturn(rule());
        when(payrollRuleItemMapper.selectList(any())).thenReturn(ruleItems());
        when(payrollMapper.selectList(any())).thenReturn(List.of(other));
        when(payrollMapper.selectRebuildTargetIds(anyLong(), anyString(), anyString(), anyList())).thenReturn(List.of());
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(EMPLOYEE_ID, "张三", 9L)));

        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setMonth(MONTH);
        request.setRuleId(RULE_ID);

        PayrollGenerateVO vo = service.generate(request);
        assertEquals(1, vo.getCreated(), "他站（站 8）非可覆盖单不得阻断站 9 生成");

        // 同站（站 8）目标 → 阻断 9405
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(EMPLOYEE_ID, "张三", 8L)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(request));
        assertEquals(ErrorCode.FINANCE_PAYROLL_GENERATED.getCode(), ex.getCode());
        verify(payrollMapper, never()).deletePhysicallyByIds(anyList());
    }

    // ==================== I-7 留痕查询与裁剪 ====================

    @Test
    @DisplayName("I-7：非 ADMIN 仅返回 action/time/reason/toStatus（服务端裁剪 before/after/operator_*）")
    void logsTrimmedForNonAdmin() {
        seedPayroll("PUBLISHED");
        when(payrollLogMapper.selectList(any())).thenReturn(List.of(log()));
        UserContext.set(new LoginUser(EMPLOYEE_ID, "张三", "STAFF", "jti", String.valueOf(STATION_ID)));

        List<PayrollLogVO> logs = service.logs(PAYROLL_ID);

        assertEquals(1, logs.size());
        PayrollLogVO vo = logs.get(0);
        assertEquals("OBJECTION", vo.getAction());
        assertEquals("OBJECTED", vo.getToStatus());
        assertEquals("金额有误", vo.getReason());
        assertNotNull(vo.getTime());
        assertNull(vo.getOperatorId(), "非 ADMIN 不得返回操作人 id");
        assertNull(vo.getOperatorName());
        assertNull(vo.getOperatorRole());
        assertNull(vo.getFromStatus());
        assertNull(vo.getBefore(), "非 ADMIN 不得返回 before 快照");
        assertNull(vo.getAfter());
        assertNull(vo.getRemark());
    }

    @Test
    @DisplayName("I-7：ADMIN 返回全量字段（含 before/after 解析）")
    void logsFullForAdmin() {
        seedPayroll("PUBLISHED");
        when(payrollLogMapper.selectList(any())).thenReturn(List.of(log()));
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        PayrollLogVO vo = service.logs(PAYROLL_ID).get(0);

        assertEquals("OBJECTION", vo.getAction());
        assertEquals("PUBLISHED", vo.getFromStatus());
        assertEquals(EMPLOYEE_ID, vo.getOperatorId());
        assertNotNull(vo.getBefore(), "ADMIN 可见 before 快照");
        assertNotNull(vo.getAfter());
    }

    @Test
    @DisplayName("I-7：越权（非本人）→ 9404；状态不可见 → 9403")
    void logsPermission() {
        seedPayroll("PUBLISHED");
        UserContext.set(new LoginUser(99L, "李四", "STAFF", "jti", String.valueOf(STATION_ID)));
        assertEquals(ErrorCode.FINANCE_PAYROLL_NO_PERMISSION.getCode(),
                assertThrows(BusinessException.class, () -> service.logs(PAYROLL_ID)).getCode());

        seedPayroll("DRAFT");
        UserContext.set(new LoginUser(EMPLOYEE_ID, "张三", "STAFF", "jti", String.valueOf(STATION_ID)));
        assertEquals(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> service.logs(PAYROLL_ID)).getCode());
    }

    // ==================== 夹具 ====================

    private Payroll seedPayroll(String status) {
        Payroll payroll = new Payroll();
        payroll.setId(PAYROLL_ID);
        payroll.setPayrollNo("PAY-202609-11");
        payroll.setEmployeeId(EMPLOYEE_ID);
        payroll.setStationId(STATION_ID);
        payroll.setMonth(MONTH);
        payroll.setBillType("MONTHLY");
        payroll.setRuleId(RULE_ID);
        payroll.setRuleName("默认计薪规则");
        payroll.setAdditionTotal(new BigDecimal("5000.00"));
        payroll.setDeductionTotal(new BigDecimal("100.00"));
        payroll.setGrossAmount(new BigDecimal("5000.00"));
        payroll.setNetAmount(new BigDecimal("4900.00"));
        payroll.setStatus(status);
        payroll.setIsDeleted(0);
        payrollStore.removeIf(row -> PAYROLL_ID.equals(row.getId()));
        payrollStore.add(payroll);
        when(payrollMapper.selectById(PAYROLL_ID)).thenReturn(payroll);
        return payroll;
    }

    private void seedItem(String key, String name, String type, String source, String amount, int sortOrder) {
        PayrollItem item = new PayrollItem();
        item.setId(++itemSeq);
        item.setPayrollId(PAYROLL_ID);
        item.setItemKey(key);
        item.setItemName(name);
        item.setItemType(type);
        item.setSource(source);
        item.setAmount(new BigDecimal(amount));
        item.setDetail("规则计算");
        item.setSortOrder(sortOrder);
        item.setIsDeleted(0);
        if (itemStore.stream().noneMatch(row -> key.equals(row.getItemKey()))) {
            itemStore.add(item);
        }
    }

    /** 覆盖重建测试：明细查询只返回 MANUAL 项（对齐 loadPreservedManual 的 source=MANUAL 过滤） */
    private List<PayrollItem> manualOnly() {
        List<PayrollItem> result = new ArrayList<>();
        for (PayrollItem row : itemStore) {
            if ("MANUAL".equals(row.getSource())) {
                result.add(row);
            }
        }
        return result;
    }

    private List<PayrollItem> itemsOf(Long payrollId) {
        List<PayrollItem> result = new ArrayList<>();
        for (PayrollItem row : itemStore) {
            if (payrollId.equals(row.getPayrollId())) {
                result.add(row);
            }
        }
        result.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        return result;
    }

    private Payroll findPayroll(Long id) {
        for (Payroll row : payrollStore) {
            if (row.getId().equals(id)) {
                return row;
            }
        }
        return null;
    }

    private PayrollRule rule() {
        PayrollRule rule = new PayrollRule();
        rule.setId(RULE_ID);
        rule.setRuleName("默认计薪规则");
        rule.setStatus(1);
        return rule;
    }

    private List<PayrollRuleItem> ruleItems() {
        List<PayrollRuleItem> items = new ArrayList<>();
        items.add(ruleItem("BASE", "基本工资", "ADDITION", "FIXED", 1));
        items.add(ruleItem("FINE", "罚款", "DEDUCTION", "ATTENDANCE", 1));
        return items;
    }

    private PayrollRuleItem ruleItem(String key, String name, String type, String source, int enabled) {
        PayrollRuleItem item = new PayrollRuleItem();
        item.setItemKey(key);
        item.setItemName(name);
        item.setItemType(type);
        item.setSource(source);
        item.setEnabled(enabled);
        return item;
    }

    private Employee employee(Long id, String name) {
        return employee(id, name, STATION_ID);
    }

    private Employee employee(Long id, String name, Long stationId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setRealName(name);
        employee.setStationId(stationId);
        employee.setStatus(1);
        return employee;
    }

    private Station station() {
        Station station = new Station();
        station.setId(STATION_ID);
        station.setStationName("测试驿站");
        return station;
    }

    private PayrollItemAddRequest addRequest(String type, String name, String amount, String reason) {
        PayrollItemAddRequest request = new PayrollItemAddRequest();
        request.setItemType(type);
        request.setItemName(name);
        request.setAmount(amount);
        request.setReason(reason);
        return request;
    }

    private PayrollItemAdjustRequest adjust(String key, String amount) {
        PayrollItemAdjustRequest item = new PayrollItemAdjustRequest();
        item.setKey(key);
        item.setAmount(amount);
        return item;
    }

    private PayrollLog log() {
        PayrollLog row = new PayrollLog();
        row.setId(1L);
        row.setPayrollId(PAYROLL_ID);
        row.setEmployeeId(EMPLOYEE_ID);
        row.setMonth(MONTH);
        row.setAction("OBJECTION");
        row.setOperatorId(EMPLOYEE_ID);
        row.setOperatorName("张三");
        row.setOperatorRole("STAFF");
        row.setOperatorType("USER");
        row.setTime(java.time.LocalDateTime.now());
        row.setFromStatus("PUBLISHED");
        row.setToStatus("OBJECTED");
        row.setReason("金额有误");
        row.setBefore("{\"netAmount\":4900}");
        row.setAfter("{\"netAmount\":4900}");
        row.setRemark("排障备注");
        return row;
    }

    private void assert9413(org.junit.jupiter.api.function.Executable action) {
        BusinessException ex = assertThrows(BusinessException.class, action);
        assertEquals(ErrorCode.FINANCE_PAYROLL_ARCHIVED.getCode(), ex.getCode());
        assertEquals(9413, ex.getCode());
    }

    /** 取最近一次 update 的 SET 子句（列名 + 占位符），用于断言写回哪些列（替身无法真读库） */
    private String lastUpdateSqlSet() {
        assertTrue(!updateWrappers.isEmpty(), "应至少有一次 update 写回");
        com.baomidou.mybatisplus.core.conditions.update.Update<?, ?> update =
                (com.baomidou.mybatisplus.core.conditions.update.Update<?, ?>)
                        updateWrappers.get(updateWrappers.size() - 1);
        String sqlSet = update.getSqlSet();
        return sqlSet == null ? "" : sqlSet;
    }

    private static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }
}
