package com.qiujie.controller.workorder;

import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.workorder.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工单字面量路由优先级回归（架构 §1.4.7 / §6.4 第 3 项，本批追加）。
 * <p>
 * 结论复核：Spring Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），
 * {@code /dispatch-rules}、{@code /auto-dispatch} 优先于 {@code /{id}}，<b>无需 @Order</b>。
 * 若被 {@code /{id}} 吞掉，Long 转换失败会返回 code 400；故断言 code=200 即证明字面量端点可达。
 * <p>
 * standalone MockMvc 不注册拦截器（角色门槛与公开端点放行由静态注解 + 服务器阶段端到端冒烟覆盖）。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class WorkOrderRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(workOrderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /work-orders/dispatch-rules 优先于 GET /work-orders/{id}")
    void dispatchRulesLiteralWinsOverIdVariable() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/dispatch-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /work-orders/auto-dispatch 优先于 /work-orders/{id}")
    void autoDispatchLiteralWinsOverIdVariable() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/auto-dispatch")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("列表端点可达（GET /work-orders）")
    void listReachable() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("详情仍按数字 id 可达（GET /work-orders/1）")
    void detailStillReachableByNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/1"))
                .andExpect(status().isOk());
    }
}
