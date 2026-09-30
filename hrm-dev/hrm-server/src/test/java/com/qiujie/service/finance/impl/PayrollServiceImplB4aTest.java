package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.finance.PayrollManualAdjustmentQuery;
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
import com.qiujie.service.finance.support.PayrollNotifySupport;
import com.qiujie.service.finance.support.PayrollResolverRegistry;
import com.qiujie.util.JsonUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.finance.PayrollManualAdjustmentEmployeeVO;
import com.qiujie.vo.finance.PayrollManualAdjustmentSummaryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B4a 批次服务层单测（Mockito，无 DB）：发布/异议通知投递前置（type 8 仅 PUBLISHED、type 9 → 管理员）、
 * 手工加/扣款对账汇总（含覆盖重建后按 employee_id+month 汇总）。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollServiceImplB4aTest {

    private static final Long PAYROLL_ID = 500L;
    private static final Long EMPLOYEE_ID = 11L;
    private static final Long OTHER_EMPLOYEE_ID = 22L;
    private static final String MONTH = "2026-09";

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
    private PayrollLogMapper payrollLogMapper;
    private EmployeeMapper employeeMapper;
    private PayrollNotifySupport notifySupport;
    private PayrollServiceImpl service;

    @BeforeEach
    void setUp() {
        payrollMapper = mock(PayrollMapper.class);
        PayrollItemMapper payrollItemMapper = mock(PayrollItemMapper.class);
        PayrollRuleMapper payrollRuleMapper = mock(PayrollRuleMapper.class);
        PayrollRuleItemMapper payrollRuleItemMapper = mock(PayrollRuleItemMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        StationMapper stationMapper = mock(StationMapper.class);
        PayrollContextProvider contextProvider = mock(PayrollContextProvider.class);
        PayrollResolverRegistry resolverRegistry = mock(PayrollResolverRegistry.class);
        payrollLogMapper = mock(PayrollLogMapper.class);
        notifySupport = mock(PayrollNotifySupport.class);

        service = new PayrollServiceImpl(payrollMapper, payrollItemMapper, payrollRuleMapper,
                payrollRuleItemMapper, employeeMapper, stationMapper, contextProvider, resolverRegistry,
                new AlgoProperties(), payrollLogMapper, notifySupport);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== 通知投递前置 ====================

    @Test
    @DisplayName("type 8：publish 后 DB 状态确已落 PUBLISHED → 投递员工本人")
    void publish_notifiesOnlyWhenPublished() {
        Payroll approved = payroll("APPROVED");
        Payroll published = payroll("PUBLISHED");
        when(payrollMapper.selectById(PAYROLL_ID)).thenReturn(approved, published);
        when(payrollMapper.update(any(), any())).thenReturn(1);
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        service.publish(publishIds(PAYROLL_ID));

        verify(notifySupport).notifyPublished(published);
    }

    @Test
    @DisplayName("type 8：落库状态仍非 PUBLISHED → 绝不投递（防草稿/待审推给员工）")
    void publish_doesNotNotifyWhenNotPublished() {
        Payroll approved = payroll("APPROVED");
        when(payrollMapper.selectById(PAYROLL_ID)).thenReturn(approved, approved);
        when(payrollMapper.update(any(), any())).thenReturn(1);
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));

        service.publish(publishIds(PAYROLL_ID));

        verify(notifySupport, never()).notifyPublished(any());
    }

    @Test
    @DisplayName("type 9：objection 落 OBJECTED 后投递管理员")
    void objection_notifiesAdmins() {
        Payroll published = payroll("PUBLISHED");
        Payroll objected = payroll("OBJECTED");
        when(payrollMapper.selectById(PAYROLL_ID)).thenReturn(published, objected);
        when(payrollMapper.update(any(), any())).thenReturn(1);
        UserContext.set(new LoginUser(EMPLOYEE_ID, "张三", "STAFF", "jti", "8"));

        service.objection(PAYROLL_ID, "金额有误");

        verify(notifySupport).notifyObjection(objected);
    }

    // ==================== 对账汇总（I-10） ====================

    @Test
    @DisplayName("对账汇总：按员工汇总加/扣款与净影响，并给合计行（含覆盖重建后仍按 employee_id+month 可达）")
    void manualAdjustmentSummary_aggregates() {
        // 覆盖重建会更换 payroll_id：同一员工两条留痕指向不同 payroll_id，仍须按 employee_id+month 合并
        PayrollLog addForEmp11 = itemAddLog(EMPLOYEE_ID, 500L, "ADDITION", "200.00");
        PayrollLog deductForEmp11 = itemAddLog(EMPLOYEE_ID, 501L, "DEDUCTION", "30.00");
        PayrollLog addForEmp22 = itemAddLog(OTHER_EMPLOYEE_ID, 600L, "ADDITION", "50.00");
        when(payrollLogMapper.selectList(any())).thenReturn(List.of(addForEmp11, deductForEmp11, addForEmp22));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(EMPLOYEE_ID, "张三"), employee(OTHER_EMPLOYEE_ID, "李四")));

        PayrollManualAdjustmentSummaryVO summary = service.manualAdjustmentSummary(query(MONTH, null));

        assertEquals(2, summary.getList().size());
        PayrollManualAdjustmentEmployeeVO first = summary.getList().get(0);
        assertEquals(EMPLOYEE_ID, first.getEmployeeId());
        assertEquals("张三", first.getEmployeeName());
        assertEquals(1, first.getAdditionCount());
        assertEquals(1, first.getDeductionCount());
        assertAmount("200.00", first.getAdditionTotal());
        assertAmount("30.00", first.getDeductionTotal());
        assertAmount("170.00", first.getNetImpact());

        PayrollManualAdjustmentEmployeeVO total = summary.getTotal();
        assertEquals("合计", total.getEmployeeName());
        assertEquals(2, total.getAdditionCount());
        assertEquals(1, total.getDeductionCount());
        assertAmount("250.00", total.getAdditionTotal());
        assertAmount("30.00", total.getDeductionTotal());
        assertAmount("220.00", total.getNetImpact());
    }

    @Test
    @DisplayName("对账汇总：驿站无员工 → 空列表 + 零合计")
    void manualAdjustmentSummary_stationWithoutEmployee() {
        when(employeeMapper.selectList(any())).thenReturn(List.of());

        PayrollManualAdjustmentSummaryVO summary = service.manualAdjustmentSummary(query(MONTH, 8L));

        assertNotNull(summary.getTotal());
        assertEquals(0, summary.getList().size());
        assertEquals(0, summary.getTotal().getAdditionCount());
        assertAmount("0", summary.getTotal().getNetImpact());
    }

    @Test
    @DisplayName("对账汇总：month 缺失 / 非法 → 400")
    void manualAdjustmentSummary_invalidMonth() {
        for (String month : new String[]{null, "", "2026-9", "202609"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.manualAdjustmentSummary(query(month, null)));
            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("对账汇总：纳入 ITEM_UPDATE——加款项增额为正、扣款项减额亦为正（净影响=变动后−变动前）")
    void manualAdjustmentSummary_includesItemUpdate() {
        // ITEM_ADD：加款 +200、扣款 30（同一留痕含两条明细，addCount 仍为 1）
        PayrollLog add = itemAddLog(EMPLOYEE_ID, 500L, List.of(
                item("A1", "ADDITION", "200.00"),
                item("A2", "DEDUCTION", "30.00")));
        // ITEM_UPDATE：加款项 100→250（+150）；扣款项 80→50（对实发 +30，因扣款减少）
        PayrollLog update = itemUpdateLog(EMPLOYEE_ID, 500L,
                List.of(item("K1", "ADDITION", "100.00"), item("K2", "DEDUCTION", "80.00")),
                List.of(item("K1", "ADDITION", "250.00"), item("K2", "DEDUCTION", "50.00")));
        when(payrollLogMapper.selectList(any())).thenReturn(List.of(add, update));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(EMPLOYEE_ID, "张三")));

        PayrollManualAdjustmentEmployeeVO row = service.manualAdjustmentSummary(query(MONTH, null)).getList().get(0);

        // 既有字段（ITEM_ADD 口径）语义不变
        assertEquals(1, row.getAdditionCount());
        assertAmount("200.00", row.getAdditionTotal());
        assertEquals(1, row.getDeductionCount());
        assertAmount("30.00", row.getDeductionTotal());
        assertAmount("170.00", row.getNetImpact());
        // 新增：动作级笔数
        assertEquals(1, row.getAddCount());
        assertEquals(1, row.getUpdateCount());
        // 改金额方向：K1 +150（正）、K2 扣款减少 +30（正）→ 净增 180
        assertAmount("180.00", row.getUpdateIncreaseTotal());
        assertAmount("0", row.getUpdateDecreaseTotal());
        // 总净影响 = 170 + 180 − 0
        assertAmount("350.00", row.getTotalNetImpact());
    }

    @Test
    @DisplayName("对账汇总：ITEM_UPDATE 净减方向——加款减少、扣款增加均计入 updateDecreaseTotal（负向）")
    void manualAdjustmentSummary_itemUpdateDecreaseNegative() {
        PayrollLog update = itemUpdateLog(EMPLOYEE_ID, 500L,
                List.of(item("K1", "ADDITION", "300.00"), item("K2", "DEDUCTION", "20.00")),
                List.of(item("K1", "ADDITION", "100.00"), item("K2", "DEDUCTION", "60.00")));
        when(payrollLogMapper.selectList(any())).thenReturn(List.of(update));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(EMPLOYEE_ID, "张三")));

        PayrollManualAdjustmentEmployeeVO row = service.manualAdjustmentSummary(query(MONTH, null)).getList().get(0);

        assertEquals(0, row.getAddCount());
        assertEquals(1, row.getUpdateCount());
        assertAmount("0", row.getUpdateIncreaseTotal());
        // 加款 300→100：−200；扣款 20→60：−40（扣款增加使实发减少）→ 合计净减 240
        assertAmount("240.00", row.getUpdateDecreaseTotal());
        assertAmount("-240.00", row.getTotalNetImpact());
    }

    @Test
    @DisplayName("自动提交跳过失痕：以 SYSTEM 主体写 payroll_log(AUTO_SUBMIT_SKIPPED)，单据仍 DRAFT")
    void recordAutoSubmitSkipped_writesSystemTrace() {
        when(payrollMapper.selectById(PAYROLL_ID)).thenReturn(payroll("DRAFT"));

        service.recordAutoSubmitSkipped(PAYROLL_ID, "BusinessException(code=9403): 当前状态不允许该操作");

        ArgumentCaptor<PayrollLog> captor = ArgumentCaptor.forClass(PayrollLog.class);
        verify(payrollLogMapper).insert(captor.capture());
        PayrollLog row = captor.getValue();
        assertEquals("AUTO_SUBMIT_SKIPPED", row.getAction());
        assertEquals("SYSTEM", row.getOperatorType());
        assertEquals("DRAFT", row.getFromStatus());
        assertEquals("DRAFT", row.getToStatus());
        assertEquals(EMPLOYEE_ID, row.getEmployeeId());
        assertEquals(MONTH, row.getMonth());
    }

    // ==================== helpers ====================

    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "金额不一致，期望 " + expected + " 实际 " + actual);
    }

    private PayrollPublishRequest publishIds(Long... ids) {
        PayrollPublishRequest request = new PayrollPublishRequest();
        request.setIds(List.of(ids));
        return request;
    }

    private PayrollManualAdjustmentQuery query(String month, Long stationId) {
        PayrollManualAdjustmentQuery query = new PayrollManualAdjustmentQuery();
        query.setMonth(month);
        query.setStationId(stationId);
        return query;
    }

    private Payroll payroll(String status) {
        Payroll payroll = new Payroll();
        payroll.setId(PAYROLL_ID);
        payroll.setEmployeeId(EMPLOYEE_ID);
        payroll.setMonth(MONTH);
        payroll.setStatus(status);
        return payroll;
    }

    private PayrollLog itemAddLog(Long employeeId, Long payrollId, String itemType, String amount) {
        return itemAddLog(employeeId, payrollId, List.of(item("MANUAL_202609_1", itemType, amount)));
    }

    /** ITEM_ADD 留痕：after.items 可含多条明细（加/扣各计），一条留痕仍只计 addCount 一次 */
    private PayrollLog itemAddLog(Long employeeId, Long payrollId, List<Map<String, Object>> items) {
        PayrollLog row = new PayrollLog();
        row.setId(1L);
        row.setPayrollId(payrollId);
        row.setEmployeeId(employeeId);
        row.setMonth(MONTH);
        row.setAction("ITEM_ADD");
        Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("items", items);
        row.setAfter(JsonUtil.write(after));
        return row;
    }

    private Employee employee(Long id, String realName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setRealName(realName);
        employee.setRole("STAFF");
        employee.setStatus(1);
        return employee;
    }

    /** ITEM_UPDATE 留痕：before / after 各含 items[{itemKey,itemType,itemName,amount}]，按 itemKey 配对计差 */
    private PayrollLog itemUpdateLog(Long employeeId, Long payrollId,
                                     List<Map<String, Object>> beforeItems, List<Map<String, Object>> afterItems) {
        PayrollLog row = new PayrollLog();
        row.setId(2L);
        row.setPayrollId(payrollId);
        row.setEmployeeId(employeeId);
        row.setMonth(MONTH);
        row.setAction("ITEM_UPDATE");
        Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("items", beforeItems);
        row.setBefore(JsonUtil.write(before));
        Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("items", afterItems);
        row.setAfter(JsonUtil.write(after));
        return row;
    }

    private Map<String, Object> item(String itemKey, String itemType, String amount) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("itemKey", itemKey);
        item.put("itemType", itemType);
        item.put("itemName", "手工项");
        item.put("amount", new BigDecimal(amount));
        return item;
    }
}