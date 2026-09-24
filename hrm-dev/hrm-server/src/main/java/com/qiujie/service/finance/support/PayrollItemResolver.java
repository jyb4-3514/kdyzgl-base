package com.qiujie.service.finance.support;

import java.util.Map;

/**
 * 计薪项来源解析器（注册表模式，算法 S2）。
 * <p>
 * 每个实现声明自己负责的 {@link #source()}，由 {@link PayrollResolverRegistry} 以
 * {@code Map<source, resolver>} 注册分发。<b>对扩展开放、对修改关闭</b>：
 * 新增来源（如 TENURE 工龄）只新增一个实现类并声明 source，核心算薪代码零改动。
 */
public interface PayrollItemResolver {

    /** 本解析器负责的来源（取 {@link PayrollSource} 名称） */
    String source();

    /**
     * 解析单项金额与解释文案。
     *
     * @param params 规则项参数（JSON 反序列化后的 Map，可为 null）
     * @param ctx    算薪上下文（定薪 / 考勤 / KPI，均可为 null）
     * @return 金额与解释文案（缺失数据一律按 0 计并写文案，不抛异常）
     */
    PayrollItemAmount resolve(Map<String, Object> params, PayrollCalcContext ctx);
}
