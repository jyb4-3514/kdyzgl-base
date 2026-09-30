package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollItem;
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
import com.qiujie.vo.finance.PayrollGenerateVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 工资单批量生成「同账期重复生成幂等」单测（Mockito 单测，无需 DB）。
 * <p>
 * 复现并锁死缺陷：旧实现 {@code deleteExisting} 走 {@code BaseMapper#delete}，受 {@code @TableLogic}
 * 影响只置 {@code is_deleted=1}，重复生成物理行只增不减（服务器实测 2026-10 两次 → 126 行 = 63 × 2）。
 * 断言重跑后物理行数不增长、(employee_id, month) 唯一、金额与明细逐位一致、无孤儿明细。
 * <p>
 * 用「带逻辑删除语义的内存仓库」替身：{@code selectList} 只返回 {@code is_deleted=0}（供 9405 守卫），
 * 自定义物理删除方法直接删行，从而可判定物理堆积是否发生。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollServiceImplGenerateIdempotencyTest {

    /**
     * 纯单测无 Spring / MyBatis-Plus 上下文：被测代码在查询旧单时使用 LambdaQueryWrapper，
     * 其列名解析依赖已注册的 TableInfo（含 lambda 缓存）；未注册即抛
     * {@code MybatisPlusException: can not find lambda cache for this entity}。
     * 故在用例前主动注册两个实体的元数据（与 TrustedDeviceRegistryTest 同一根因与手法）。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Payroll.class);
        TableInfoHelper.initTableInfo(assistant, PayrollItem.class);
        // 生成链路还会按员工 / 驿站 / 计薪规则实体构造 lambda 条件，故一次性全部注册，避免逐个补漏
        TableInfoHelper.initTableInfo(assistant, Employee.class);
        TableInfoHelper.initTableInfo(assistant, Station.class);
        TableInfoHelper.initTableInfo(assistant, PayrollRule.class);
        TableInfoHelper.initTableInfo(assistant, PayrollRuleItem.class);
    }

    private static final String MONTH = "2026-10";
    private static final Long RULE_ID = 1L;
    /** 启用项数（停用项不计） */
    private static final int RULE_ITEM_COUNT = 2;

    private final List<Long> employeeIds = List.of(11L, 22L);
    /** 内存仓库：含逻辑删除行（is_deleted=1），用于判定物理堆积 */
    private final List<Payroll> payrollStore = new ArrayList<>();
    private final List<PayrollItem> itemStore = new ArrayList<>();
    private long payrollSeq = 100L;
    private long itemSeq = 1000L;

    private PayrollMapper payrollMapper;
    private PayrollItemMapper payrollItemMapper;
    private PayrollServiceImpl service;

    @BeforeEach
    void setUp() {
        payrollStore.clear();
        itemStore.clear();
        payrollSeq = 100L;
        itemSeq = 1000L;

        payrollMapper = mock(PayrollMapper.class);
        payrollItemMapper = mock(PayrollItemMapper.class);
        PayrollRuleMapper payrollRuleMapper = mock(PayrollRuleMapper.class);
        PayrollRuleItemMapper payrollRuleItemMapper = mock(PayrollRuleItemMapper.class);
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        StationMapper stationMapper = mock(StationMapper.class);
        PayrollContextProvider contextProvider = mock(PayrollContextProvider.class);
        PayrollResolverRegistry resolverRegistry = mock(PayrollResolverRegistry.class);
        PayrollLogMapper payrollLogMapper = mock(PayrollLogMapper.class);
        // 真实配置对象（默认值即出厂口径：allowNegativeNet=false）
        AlgoProperties algoProperties = new AlgoProperties();

        service = new PayrollServiceImpl(payrollMapper, payrollItemMapper, payrollRuleMapper,
                payrollRuleItemMapper, employeeMapper, stationMapper, contextProvider, resolverRegistry,
                algoProperties, payrollLogMapper, mock(PayrollNotifySupport.class));

        when(payrollRuleMapper.selectById(RULE_ID)).thenReturn(rule());
        when(payrollRuleItemMapper.selectList(any())).thenAnswer(inv -> ruleItems());
        when(employeeMapper.selectList(any())).thenAnswer(inv -> employees());
        when(contextProvider.contextOf(anyLong(), anyString())).thenReturn(PayrollCalcContext.empty());
        when(resolverRegistry.resolve(anyString(), any(), any())).thenAnswer(inv -> {
            String source = inv.getArgument(0);
            return new PayrollItemAmount("FIXED".equals(source)
                    ? new BigDecimal("5000.00") : new BigDecimal("100.00"), "规则计算");
        });

        // 逻辑删除视图：守卫查询只看到 is_deleted=0 的行（MyBatis-Plus 行为等价替身）
        when(payrollMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(activeRows()));

        when(payrollMapper.insert(any(Payroll.class))).thenAnswer(inv -> {
            Payroll row = inv.getArgument(0);
            row.setId(++payrollSeq);
            row.setIsDeleted(0);
            payrollStore.add(row);
            return 1;
        });
        when(payrollItemMapper.insert(any(PayrollItem.class))).thenAnswer(inv -> {
            PayrollItem row = inv.getArgument(0);
            row.setId(++itemSeq);
            row.setIsDeleted(0);
            itemStore.add(row);
            return 1;
        });
        // 物理删除替身（自定义 SQL 不被逻辑删除改写）
        when(payrollItemMapper.deletePhysicallyByPayrollIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            int before = itemStore.size();
            itemStore.removeIf(item -> ids.contains(item.getPayrollId()));
            return before - itemStore.size();
        });
        when(payrollMapper.deletePhysicallyByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            int before = payrollStore.size();
            payrollStore.removeIf(row -> ids.contains(row.getId()));
            return before - payrollStore.size();
        });
        // 覆盖范围查询替身：取同员工同月同类型且状态可覆盖的 id（含逻辑删除残留）
        when(payrollMapper.selectRebuildTargetIds(anyLong(), anyString(), anyString(), anyList()))
                .thenAnswer(inv -> {
                    Long employeeId = inv.getArgument(0);
                    String month = inv.getArgument(1);
                    String billType = inv.getArgument(2);
                    List<String> statuses = inv.getArgument(3);
                    List<Long> ids = new ArrayList<>();
                    for (Payroll row : payrollStore) {
                        if (employeeId.equals(row.getEmployeeId()) && month.equals(row.getMonth())
                                && billType.equals(row.getBillType()) && statuses.contains(row.getStatus())) {
                            ids.add(row.getId());
                        }
                    }
                    return ids;
                });
    }

    // ==================== 用例 ====================

    @Test
    @DisplayName("连续两次生成：物理行数不增长、(employee_id,month) 唯一、金额与明细逐位一致、无孤儿")
    void regenerateIsIdempotent() {
        PayrollGenerateVO first = service.generate(request(MONTH));
        Map<Long, String> firstFingerprint = fingerprint(activeRows());

        PayrollGenerateVO second = service.generate(request(MONTH));

        assertEquals(employeeIds.size(), first.getCreated());
        assertEquals(employeeIds.size(), second.getCreated());
        // 关键断言：物理行数等于员工数（旧逻辑删除实现会翻倍成 2N）
        assertEquals(employeeIds.size(), payrollStore.size(), "重复生成不得物理堆积旧单");
        assertEquals(employeeIds.size(), distinctEmployeeMonth().size(), "(employee_id, month) 应唯一");
        // 两次生成结果逐位一致（金额 + 明细项 key/金额/文案）
        assertEquals(firstFingerprint, fingerprint(activeRows()), "重复生成结果应与首次逐位一致");
        assertEquals(employeeIds.size() * RULE_ITEM_COUNT, itemStore.size(), "每单明细数一致");
        assertNoOrphanItems();
    }

    @Test
    @DisplayName("同月存在非草稿单 → 9405，无任何删除/新增（既有保护不回归）")
    void nonEditableStatusStillBlocksWith9405() {
        for (String status : List.of("PENDING_APPROVAL", "APPROVED", "PUBLISHED", "CONFIRMED")) {
            resetStore();
            seedMonthly(employeeIds.get(0), status, false, RULE_ITEM_COUNT);
            int rowsBefore = payrollStore.size();
            int itemsBefore = itemStore.size();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(request(MONTH)), "状态 " + status + " 应阻断整批");
            assertEquals(ErrorCode.FINANCE_PAYROLL_GENERATED.getCode(), ex.getCode());
            assertEquals(rowsBefore, payrollStore.size(), "阻断时不得部分成功");
            assertEquals(itemsBefore, itemStore.size());
            verify(payrollMapper, never()).deletePhysicallyByIds(anyList());
            verify(payrollItemMapper, never()).deletePhysicallyByPayrollIds(anyList());
        }
    }

    @Test
    @DisplayName("混合场景：已有草稿员工被覆盖重建、无草稿员工新建，旧草稿明细不残留")
    void mixedExistingDraft() {
        Long withDraft = employeeIds.get(0);
        // 旧草稿仅 1 条明细，用于验证「旧明细被物理清除」
        seedMonthly(withDraft, "DRAFT", false, 1);
        Long seededId = payrollStore.get(0).getId();

        PayrollGenerateVO vo = service.generate(request(MONTH));

        assertEquals(employeeIds.size(), vo.getCreated());
        assertEquals(employeeIds.size(), payrollStore.size(), "旧草稿应被物理替换而非新增");
        assertEquals(employeeIds.size(), distinctEmployeeMonth().size());
        assertTrue(payrollStore.stream().noneMatch(row -> seededId.equals(row.getId())),
                "旧草稿行应被物理删除");
        for (Payroll row : activeRows()) {
            assertEquals(RULE_ITEM_COUNT, itemsOf(row.getId()).size(), "每单明细数应等于启用项数");
        }
        assertEquals(employeeIds.size() * RULE_ITEM_COUNT, itemStore.size());
        assertNoOrphanItems();
    }

    @Test
    @DisplayName("历史逻辑删除残留被清理：重跑后物理行数回落为员工数")
    void purgeSoftDeletedResidual() {
        // 复现缺陷现场：同月同员工既有「已逻辑删除的 DRAFT 残留行」又有活跃 DRAFT
        seedMonthly(employeeIds.get(0), "DRAFT", true, RULE_ITEM_COUNT);
        seedMonthly(employeeIds.get(0), "DRAFT", false, RULE_ITEM_COUNT);

        service.generate(request(MONTH));

        assertEquals(employeeIds.size(), payrollStore.size(), "残留软删行应被一并物理清除");
        assertTrue(payrollStore.stream().noneMatch(this::isDeleted), "不得再存留 is_deleted=1 的行");
        assertEquals(employeeIds.size(), distinctEmployeeMonth().size());
        assertNoOrphanItems();
    }

    @Test
    @DisplayName("物理删除范围仅 DRAFT/REJECTED：逻辑删除的 PUBLISHED 残留不被误删")
    void nonEditableResidualUntouched() {
        seedMonthly(employeeIds.get(0), "PUBLISHED", true, RULE_ITEM_COUNT);

        service.generate(request(MONTH));

        assertTrue(payrollStore.stream().anyMatch(row -> "PUBLISHED".equals(row.getStatus())),
                "已发布单据不得被覆盖重建删除");
        assertNoOrphanItems();
    }

    // ==================== 夹具与断言工具 ====================

    private PayrollGenerateRequest request(String month) {
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setMonth(month);
        request.setRuleId(RULE_ID);
        return request;
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
        items.add(ruleItem("OFF", "停用项", "ADDITION", "KPI", 0));
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

    private List<Employee> employees() {
        List<Employee> list = new ArrayList<>();
        for (Long id : employeeIds) {
            Employee employee = new Employee();
            employee.setId(id);
            employee.setRealName("员工" + id);
            employee.setStationId(1L);
            employee.setStatus(1);
            list.add(employee);
        }
        return list;
    }

    /** 播种一条同月 MONTHLY 单（可指定是否逻辑删除），并挂 itemCount 条明细 */
    private void seedMonthly(Long employeeId, String status, boolean logicallyDeleted, int itemCount) {
        Payroll row = new Payroll();
        row.setId(++payrollSeq);
        row.setPayrollNo("PAY-" + MONTH.replace("-", "") + "-" + employeeId);
        row.setEmployeeId(employeeId);
        row.setMonth(MONTH);
        row.setBillType("MONTHLY");
        row.setStatus(status);
        row.setNetAmount(new BigDecimal("1.00"));
        row.setIsDeleted(logicallyDeleted ? 1 : 0);
        payrollStore.add(row);
        for (int i = 0; i < itemCount; i++) {
            PayrollItem item = new PayrollItem();
            item.setId(++itemSeq);
            item.setPayrollId(row.getId());
            item.setItemKey("SEED_" + i);
            item.setItemName("播种项" + i);
            item.setItemType("ADDITION");
            item.setSource("FIXED");
            item.setAmount(new BigDecimal("1.00"));
            item.setDetail("播种");
            item.setSortOrder(i + 1);
            item.setIsDeleted(0);
            itemStore.add(item);
        }
    }

    private void resetStore() {
        payrollStore.clear();
        itemStore.clear();
        payrollSeq = 100L;
        itemSeq = 1000L;
    }

    private boolean isDeleted(Payroll row) {
        return row.getIsDeleted() != null && row.getIsDeleted() == 1;
    }

    private List<Payroll> activeRows() {
        List<Payroll> rows = new ArrayList<>();
        for (Payroll row : payrollStore) {
            if (!isDeleted(row)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(Payroll::getEmployeeId));
        return rows;
    }

    private Set<String> distinctEmployeeMonth() {
        Set<String> keys = new LinkedHashSet<>();
        for (Payroll row : payrollStore) {
            keys.add(row.getEmployeeId() + "#" + row.getMonth());
        }
        return keys;
    }

    private List<PayrollItem> itemsOf(Long payrollId) {
        List<PayrollItem> items = new ArrayList<>();
        for (PayrollItem item : itemStore) {
            if (payrollId.equals(item.getPayrollId())) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(PayrollItem::getSortOrder).thenComparing(PayrollItem::getId));
        return items;
    }

    /** 员工 → 「净额 + 明细逐位」指纹，用于跨次比对 */
    private Map<Long, String> fingerprint(List<Payroll> rows) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Payroll row : rows) {
            StringBuilder builder = new StringBuilder(String.valueOf(row.getNetAmount()));
            for (PayrollItem item : itemsOf(row.getId())) {
                builder.append('|').append(item.getItemKey()).append('=')
                        .append(item.getAmount()).append('@').append(item.getDetail());
            }
            map.put(row.getEmployeeId(), builder.toString());
        }
        return map;
    }

    private void assertNoOrphanItems() {
        Set<Long> payrollIdSet = new LinkedHashSet<>();
        for (Payroll row : payrollStore) {
            payrollIdSet.add(row.getId());
        }
        for (PayrollItem item : itemStore) {
            assertTrue(payrollIdSet.contains(item.getPayrollId()),
                    "明细存在孤儿 payroll_id=" + item.getPayrollId());
        }
    }
}
