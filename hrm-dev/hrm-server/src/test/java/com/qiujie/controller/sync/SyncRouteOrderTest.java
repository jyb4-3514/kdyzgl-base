package com.qiujie.controller.sync;

import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.sync.StationSyncConfigService;
import com.qiujie.service.sync.SyncConfigCenterService;
import com.qiujie.service.sync.SyncExportFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同步路由顺序回归（架构 §1.4.7 / §6.4 第 3 项，P9 追加）。
 * <p>
 * <b>复核结论</b>：Mock 依赖 {@code routes/index.js} 的注册顺序把配置中心整体排在 {@code syncConfigRoutes} 之前；
 * Spring Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（<b>字面量段 &gt; 变量段</b>），
 * 故 {@code /sync/configs/global|export|import} 天然优先于 {@code /sync/configs/{stationId}}，
 * <b>与控制器注册先后无关，无需 @Order 或通配改造</b>。
 * <p>
 * 断言方式：若 {@code /configs/global} 被 {@code /configs/{stationId}} 吞掉，{@code "global"} 无法转 Long，
 * 将返回 code 400；因此断言 code=200 即证字面量端点可达且优先级正确。本类同时把两个控制器一起装配，
 * 以覆盖「跨控制器同基路径」的真实场景。
 * <p>
 * standalone MockMvc 不注册拦截器（角色门槛由静态注解 + 服务器阶段端到端冒烟覆盖）。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SyncConfigCenterService centerService = mock(SyncConfigCenterService.class);
        StationSyncConfigService configService = mock(StationSyncConfigService.class);
        when(centerService.export(any())).thenReturn(new SyncExportFile("a,b".getBytes(), "同步配置_全部.csv"));
        mockMvc = MockMvcBuilders.standaloneSetup(new SyncConfigCenterController(centerService),
                        new SyncConfigController(configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /sync/configs/global 优先于 GET /sync/configs/{stationId}")
    void globalLiteralWinsOverStationVariableOnGet() throws Exception {
        mockMvc.perform(get("/api/v1/sync/configs/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /sync/configs/global 优先于 PUT /sync/configs/{stationId}")
    void globalLiteralWinsOverStationVariableOnPut() throws Exception {
        mockMvc.perform(put("/api/v1/sync/configs/global").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /sync/configs/export 优先于 GET /sync/configs/{stationId}")
    void exportLiteralWinsOverStationVariable() throws Exception {
        mockMvc.perform(get("/api/v1/sync/configs/export"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /sync/configs/import 可达（无同方法变量冲突）")
    void importReachable() throws Exception {
        mockMvc.perform(post("/api/v1/sync/configs/import").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("变量端点仍按数字站号可达（GET /sync/configs/1）")
    void stationVariableReachableByNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/sync/configs/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("配置项影响面为独立段数，不与选项路由互吞（GET /sync/config-items/{k}/impact）")
    void itemImpactReachable() throws Exception {
        mockMvc.perform(get("/api/v1/sync/config-items/data_source/impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
