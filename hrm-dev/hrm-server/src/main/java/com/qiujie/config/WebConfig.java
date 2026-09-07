package com.qiujie.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * 注意：不做任何 CORS 配置（决策 D9）——开发环境 Vite proxy、生产环境 Nginx 同域反代，前后端始终同源。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequireAdminInterceptor requireAdminInterceptor;

    public WebConfig(RequireAdminInterceptor requireAdminInterceptor) {
        this.requireAdminInterceptor = requireAdminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ADMIN 权限拦截仅作用于业务 API（认证由 JwtAuthFilter 在更外层完成）
        registry.addInterceptor(requireAdminInterceptor).addPathPatterns("/api/**");
    }
}
