package com.qiujie.controller.finance;

import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.finance.PayrollRuleService;
import com.qiujie.service.finance.PayrollService;
import com.qiujie.vo.finance.MyPayrollPageVO;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollPageVO;
import com.qiujie.vo.finance.PayrollPublishVO;
import com.qiujie.vo.finance.PayrollRuleListVO;
import com.qiujie.vo.finance.PayrollSubmitVO;
import com.qiujie.vo.finance.PayrollVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 财务路由顺序回归（字面量段优先于变量段，架构 §1.4.7 / §6.2 P6 验收⑧）。
 * <p>
 * 结论复核：Spring Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），
 * 故 {@code /finance/payrolls/my|generate|submit|publish} 优先于 {@code /finance/payrolls/{id}}，
 * <b>无需人为 @Order</b>。
 * <p>
 * 用 standalone MockMvc（不依赖 DB/Redis/容器）断言：字面量端点可达（HTTP 200）；若被变量段吞掉，
 * {@code GET /payrolls/my} 会经 {@code Long} 转换失败由 GlobalExceptionHandler 回落 code 400。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PayrollService payrollService = mock(PayrollService.class);
        PayrollRuleService ruleService = mock(PayrollRuleService.class);

        MyPayrollPageVO myPage = new MyPayrollPageVO();
        myPage.setEmployeeId(7L);
        myPage.setList(List.of());
        when(payrollService.myPayrolls(any())).thenReturn(myPage);

        PayrollGenerateVO generate = new PayrollGenerateVO();
        generate.setMonth("2026-09");
        generate.setCreated(3);
        when(payrollService.generate(any())).thenReturn(generate);

        PayrollSubmitVO submit = new PayrollSubmitVO();
        submit.setSubmitted(2);
        when(payrollService.submit(any())).thenReturn(submit);

        PayrollPublishVO publish = new PayrollPublishVO();
        publish.setPublished(1);
        when(payrollService.publish(any())).thenReturn(publish);

        PayrollPageVO page = new PayrollPageVO();
        page.setTotal(5);
        when(payrollService.list(any())).thenReturn(page);

        PayrollVO detail = new PayrollVO();
        detail.setId(12L);
        when(payrollService.detail(anyLong())).thenReturn(detail);

        when(ruleService.listRules()).thenReturn(new PayrollRuleListVO(List.of()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PayrollController(payrollService), new PayrollRuleController(ruleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("/finance/payrolls/my 字面量段优先于 /finance/payrolls/{id}")
    void myLiteralWinsOverId() throws Exception {
        mockMvc.perform(get("/api/v1/finance/payrolls/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.employeeId").value(7));
    }

    @Test
    @DisplayName("/finance/payrolls/generate 可达（不被变量段吞掉）")
    void generateReachable() throws Exception {
        mockMvc.perform(post("/api/v1/finance/payrolls/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(3));
    }

    @Test
    @DisplayName("/finance/payrolls/submit 与 /publish 可达且不误匹配 {id}/{action}")
    void submitPublishReachable() throws Exception {
        mockMvc.perform(post("/api/v1/finance/payrolls/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(2));

        mockMvc.perform(post("/api/v1/finance/payrolls/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(1));
    }

    @Test
    @DisplayName("/finance/payrolls/{id} 数字路径参数仍可达；列表字面量端点可达")
    void idAndListReachable() throws Exception {
        mockMvc.perform(get("/api/v1/finance/payrolls/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12));

        mockMvc.perform(get("/api/v1/finance/payrolls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    @DisplayName("/finance/payroll-rules 列表可达")
    void ruleListReachable() throws Exception {
        mockMvc.perform(get("/api/v1/finance/payroll-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
