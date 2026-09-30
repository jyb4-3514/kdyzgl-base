package com.qiujie.service.finance;

import com.qiujie.common.PageResult;
import com.qiujie.dto.finance.PayrollRunQuery;
import com.qiujie.dto.finance.PayrollRunTriggerRequest;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.vo.finance.PayrollRunVO;

import java.time.ZonedDateTime;

/**
 * 自动算薪运行服务（I-4 / I-5 + 调度执行，api.md §4.12.18）。
 * <p>
 * <b>事务边界</b>：本服务编排层<b>不加事务</b>；认领（Tx1）/ 生成（Tx2）/ 终态（Tx3）三段交易在
 * {@code PayrollRunTxHandler} 内各自独立提交，保证「claim 先落库对其他执行者可见」与「单驿站失败自隔离」。
 * <p>
 * <b>不新建第二套算薪逻辑</b>：执行一律委托既有 {@link PayrollService#generate}。
 */
public interface PayrollRunService {

    /** 手工触发（I-4；未启用 9415、占位/当日已尝试 9410；命中 9405 映射为 SKIPPED 结果） */
    PayrollRunVO trigger(PayrollRunTriggerRequest request);

    /** 运行记录分页（I-5） */
    PageResult<PayrollRunVO> list(PayrollRunQuery query);

    /**
     * 调度单驿站判定 + 执行（算法 §1.3 {@code executeIfDue}）。
     * <p>调用方（ticker）已完成 {@code enabled=1 ∧ station.status=1} 的过滤（判定链前置）。
     *
     * @return 实际执行（含成功 / 失败 / 跳过）的运行结果；「未到点 / 当日已尝试 / 已占位 / 撞唯一键」返回 {@code null}
     */
    PayrollRunVO executeIfDue(StationPayrollSetting setting, ZonedDateTime now);

    /** 僵死 RUNNING 回收（算法 §1.5.3）；开关关闭返回 0 */
    int reclaimStale(ZonedDateTime now);
}
