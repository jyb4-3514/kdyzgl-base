package com.qiujie.architecture;

import com.qiujie.controller.employee.EmployeeController;
import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.employee.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 路由匹配顺序回归（C-09，架构 1.4.7 / 6.4 第 3 项）。
 * <p>
 * 结论复核：Spring Boot 3 默认使用 {@code PathPatternParser}，按「模式特异性」排序（字面量段 > 变量段），
 * 因此 {@code /employees/import-template}、{@code /employees/export} 优先于 {@code /employees/{id}}，
 * <b>无需为兼容 Mock 注册顺序而人为 @Order</b>。
 * <p>
 * 本测试用 standalone MockMvc（不依赖 DB/Redis/Spring 容器；已装配 GlobalExceptionHandler）断言：
 * 字面量端点可达（HTTP 200），若被 {@code {id}} 吞掉则 Long 转换失败返回 code 400。
 * <p>
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。后续批次新增字面量端点时按 6.4 清单在本类追加断言：
 * /work-orders/dispatch-rules、/work-orders/auto-dispatch、/kpi/metrics/batch、/sync/configs/global|export|import、
 * /finance/payrolls/my|generate|submit|publish、/notifications/unread-count|read-all|publish、
 * /leave/preview|my|list|settings、/parcels/summary|trend|ranking。
 * <p>
 * <b>P9 说明</b>：{@code /sync/configs/global|export|import} 的「字面量优先于 {@code /sync/configs/{stationId}}」
 * 由专门用例 {@code com.qiujie.controller.sync.SyncRouteOrderTest} 覆盖（跨控制器同基路径的真实场景，
 * 需同时装配 {@code SyncConfigCenterController} 与 {@code SyncConfigController}），本类不重复装配。
 */
class RouteOrderRegressionTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EmployeeService employeeService = mock(EmployeeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EmployeeController(employeeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void importTemplateLiteralWinsOverIdVariable() throws Exception {
        mockMvc.perform(get("/api/v1/employees/import-template")).andExpect(status().isOk());
    }

    @Test
    void exportLiteralWinsOverIdVariable() throws Exception {
        mockMvc.perform(get("/api/v1/employees/export")).andExpect(status().isOk());
    }

    @Test
    void detailStillReachableByNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/employees/1")).andExpect(status().isOk());
    }

    @Test
    void nonNumericIdMapsToBusinessCode400() throws Exception {
        // 命中 {id} 且 Long 转换失败：GlobalExceptionHandler 统一为 HTTP 200 + code 400
        // （架构 8-8：与 Mock 的 404 存在低优先差异，前端不会发非数字 id，本批不处理）
        mockMvc.perform(get("/api/v1/employees/not-a-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
