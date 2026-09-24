package com.qiujie.controller.kpi;

import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.kpi.KpiMetricService;
import com.qiujie.service.kpi.KpiScoreService;
import com.qiujie.vo.kpi.KpiMetricBatchResultVO;
import com.qiujie.vo.kpi.KpiRankingPageVO;
import com.qiujie.vo.kpi.KpiScoreDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KPI 路由顺序回归（字面量段优先于变量段，架构 §1.4.7 / §6.4）。
 * <p>
 * 结论复核：Spring Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），
 * 故 {@code /kpi/metrics/batch} 优先于 {@code /kpi/metrics/{id}}、{@code /kpi/scores/ranking} 优先于
 * {@code /kpi/scores/{employeeId}}，<b>无需人为 @Order</b>。
 * <p>
 * 用 standalone MockMvc（不依赖 DB/Redis/容器）断言：字面量端点可达（HTTP 200）；
 * 若被变量段吞掉，则 {@code Long} 转换失败经 GlobalExceptionHandler 回落 code 400。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class KpiRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KpiMetricService metricService = mock(KpiMetricService.class);
        KpiScoreService scoreService = mock(KpiScoreService.class);

        KpiMetricBatchResultVO batch = new KpiMetricBatchResultVO();
        batch.setWeightSum(100);
        batch.setUpdated(2);
        when(metricService.saveBatch(any())).thenReturn(batch);

        KpiRankingPageVO ranking = new KpiRankingPageVO();
        ranking.setMonth("2026-09");
        ranking.setCount(3);
        ranking.setTotal(3);
        when(scoreService.ranking(any())).thenReturn(ranking);

        KpiScoreDetailVO detail = new KpiScoreDetailVO();
        detail.setEmployeeId(1L);
        detail.setMonth("2026-09");
        when(scoreService.detail(any(), any())).thenReturn(detail);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KpiMetricController(metricService), new KpiScoreController(scoreService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("/kpi/metrics/batch 字面量段优先于 /kpi/metrics/{id}")
    void metricsBatchLiteralWinsOverId() throws Exception {
        mockMvc.perform(put("/api/v1/kpi/metrics/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":1,\"weight\":40}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updated").value(2));
    }

    @Test
    @DisplayName("/kpi/scores/ranking 字面量段优先于 /kpi/scores/{employeeId}")
    void rankingLiteralWinsOverEmployeeId() throws Exception {
        mockMvc.perform(get("/api/v1/kpi/scores/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    @DisplayName("/kpi/scores/{employeeId} 数字路径参数仍可达")
    void employeeIdDetailReachable() throws Exception {
        mockMvc.perform(get("/api/v1/kpi/scores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.employeeId").value(1));
    }
}
