package com.qiujie.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * 注意：不做任何 CORS 配置（决策 D9）——开发环境 Vite proxy、生产环境 Nginx 同域反代，前后端始终同源。
 * <p>
 * 拦截链顺序（认证由 JwtAuthFilter 在更外层完成）：
 * 1. {@link RequireRolesInterceptor}——端点级角色门槛（403）；
 * 2. {@link QueryDataScopeInterceptor}——查询参数 stationId 静默收敛（L1）。
 * 角色门槛先于数据范围收敛：无权限应在触碰数据范围逻辑前直接被拒。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequireRolesInterceptor requireRolesInterceptor;
    private final QueryDataScopeInterceptor queryDataScopeInterceptor;

    public WebConfig(RequireRolesInterceptor requireRolesInterceptor,
                     QueryDataScopeInterceptor queryDataScopeInterceptor) {
        this.requireRolesInterceptor = requireRolesInterceptor;
        this.queryDataScopeInterceptor = queryDataScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireRolesInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(queryDataScopeInterceptor).addPathPatterns("/api/**");
    }
}
