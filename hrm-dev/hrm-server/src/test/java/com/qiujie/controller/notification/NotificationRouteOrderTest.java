package com.qiujie.controller.notification;

import com.qiujie.handler.GlobalExceptionHandler;
import com.qiujie.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知字面量路由优先级回归（架构 §1.4.7 / §6.4 第 3 项，本批追加）。
 * <p>
 * 结论复核：Spring Boot 3 默认 {@code PathPatternParser} 按模式特异性排序（字面量段 > 变量段），
 * {@code /unread-count}、{@code /read-all}、{@code /publish} 优先于 {@code /{id}} / {@code /{id}/read}，
 * <b>无需 @Order</b>。若 {@code /unread-count} 被 {@code /{id}} 吞掉，Long 转换失败会返回 code 400；
 * 故断言 code=200 即证明字面量端点可达。
 * <p>
 * standalone MockMvc 不注册拦截器（角色门槛由静态注解 + 服务器阶段端到端冒烟覆盖）。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段跑</b>。
 */
class NotificationRouteOrderTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationService notificationService = mock(NotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unreadCountLiteralWinsOverIdVariable() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void readAllLiteralReachable() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void publishLiteralReachable() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/publish")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void detailStillReachableByNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/1"))
                .andExpect(status().isOk());
    }

    @Test
    void listReachable() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk());
    }
}
