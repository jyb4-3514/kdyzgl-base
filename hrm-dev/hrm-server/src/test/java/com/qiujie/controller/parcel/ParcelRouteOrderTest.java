package com.qiujie.controller.parcel;

import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.parcel.ParcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 包裹字面量路由优先级回归（架构 §6.2 P10 / §1.4.7）。
 * <p>
 * 结论复核：Spring Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），
 * {@code /summary}、{@code /trend}、{@code /ranking} 优先于 {@code /{id}}，<b>无需 @Order</b>；
 * 若被 {@code /{id}} 吞掉，Long 转换失败会返回 code 400（故断言 200 即证明字面量端点可达）。
 * <p>
 * standalone MockMvc 不注册拦截器（角色门槛由静态注解 + 服务器阶段端到端冒烟覆盖）。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class ParcelRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ParcelService parcelService = mock(ParcelService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ParcelController(parcelService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /parcels/summary 优先于 GET /parcels/{id}")
    void summaryLiteralWins() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /parcels/trend 优先于 GET /parcels/{id}")
    void trendLiteralWins() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /parcels/ranking 优先于 GET /parcels/{id}")
    void rankingLiteralWins() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("列表端点可达（GET /parcels）")
    void listReachable() throws Exception {
        mockMvc.perform(get("/api/v1/parcels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("详情仍按数字 id 可达（GET /parcels/1）")
    void detailStillReachableByNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/1"))
                .andExpect(status().isOk());
    }
}
