package com.qiujie.util;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 过滤器 / 拦截器层的统一 JSON 写出。
 * <p>
 * 为什么单独抽工具：该层执行发生在 Spring MVC 之前，无法复用 {@code GlobalExceptionHandler}，
 * 若各处手拼 JSON 会产生格式漂移（如漏 data 字段）。响应体与 {@code Result} 结构保持一致：{@code {code,message,data}}。
 */
public final class ResponseWriter {

    private ResponseWriter() {
    }

    /**
     * 写出统一结构响应。
     *
     * @param httpStatus HTTP 状态码（401/403/404 需与实际语义同步）
     * @param code       业务码
     * @param message    提示文案（本系统内均为固定中文文案，不含引号，无需转义）
     */
    public static void write(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
