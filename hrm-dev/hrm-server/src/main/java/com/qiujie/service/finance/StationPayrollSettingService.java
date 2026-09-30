package com.qiujie.service.finance;

import com.qiujie.common.PageResult;
import com.qiujie.dto.finance.PayrollSettingLogQuery;
import com.qiujie.dto.finance.StationPayrollSettingQuery;
import com.qiujie.dto.finance.StationPayrollSettingSaveRequest;
import com.qiujie.vo.finance.PayrollSettingLogVO;
import com.qiujie.vo.finance.StationPayrollSettingVO;

import java.util.List;

/**
 * 驿站算薪配置服务（I-1 / I-2 / I-3 / I-9，api.md §4.12.16/§4.12.17）。
 * <p>
 * I-3 每次保存<b>必写一条</b> {@code station_payroll_setting_log}（M-9 硬要求）：首次创建 {@code CREATE}、
 * 启用 {@code 0→1} 记 {@code ENABLE}（可追溯）、停用 {@code 1→0} 记 {@code DISABLE}、其余变更记 {@code UPDATE}。
 */
public interface StationPayrollSettingService {

    /** 驿站列表 + 各站算薪配置（I-1；未配置站以 DDL 默认值填充） */
    List<StationPayrollSettingVO> list(StationPayrollSettingQuery query);

    /** 单驿站算薪配置（I-2；驿站不存在 4001、未配置 9406） */
    StationPayrollSettingVO detail(Long stationId);

    /** 保存算薪配置（I-3；算薪日非法 9407、时间非法 9408；每次保存写配置留痕） */
    StationPayrollSettingVO save(Long stationId, StationPayrollSettingSaveRequest request);

    /** 配置变更历史（I-9；驿站不存在 4001；按 time 倒序分页） */
    PageResult<PayrollSettingLogVO> logs(Long stationId, PayrollSettingLogQuery query);
}
