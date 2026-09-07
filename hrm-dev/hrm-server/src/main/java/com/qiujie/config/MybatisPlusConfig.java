package com.qiujie.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：
 * - Mapper 扫描 com.qiujie.mapper；
 * - 分页插件不指定 DbType，按实际连接自动适配方言（MySQL/PostgreSQL 通用）；
 * - 逻辑删除（is_deleted）与自动填充见 application.yml 与 MyMetaObjectHandler。
 */
@Configuration
@MapperScan("com.qiujie.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
