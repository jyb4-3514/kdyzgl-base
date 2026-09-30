package com.qiujie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 快递驿站智汇系统 一期员工管理后端服务启动类。
 * <p>
 * {@code @EnableScheduling}：唯一基础设施开关，供薪资自动算薪调度器 {@code PayrollScheduleTicker} 固定间隔轮询
 * （ADR-01；调度总开关默认关，见 {@code hrm.payroll.schedule.enabled}）。
 */
@EnableScheduling
@SpringBootApplication
public class HrmServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrmServerApplication.class, args);
    }
}
