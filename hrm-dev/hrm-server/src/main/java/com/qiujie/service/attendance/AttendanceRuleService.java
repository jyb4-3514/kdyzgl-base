package com.qiujie.service.attendance;

import com.qiujie.dto.attendance.AttendanceRuleRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.vo.attendance.AttendanceRuleVO;

import java.util.List;

/**
 * 打卡规则服务（3 接口：GET rule / GET rule/list / PUT rule）。
 * <p>
 * <b>真源统一（方案 v1.2）</b>：打卡时间的唯一真源是「该驿站启用班次」；{@code checkPeriods} /
 * {@code workStartTime} / {@code workEndTime} 均为<b>读取时由班次派生</b>的只读出参，{@code checkFrequency}
 * 只读派生（= 启用班次数 × 2）。供同域其它服务（记录/补卡）复用规则读取与出参组装，避免出参口径多写一份。
 */
public interface AttendanceRuleService {

    /** 单条规则（未配置 → 9101） */
    AttendanceRuleVO getRule(Long stationId);

    /** 全部规则（仅 ADMIN） */
    List<AttendanceRuleVO> listRules();

    /** 覆盖式保存（按驿站；不存在则首存即创建，返回保存后的规则） */
    AttendanceRuleVO saveRule(AttendanceRuleRequest request);

    /** 按驿站取规则实体（不存在返回 null；供同域复用） */
    AttendanceRule findRule(Long stationId);

    /** 规则实体 → 出参（含派生的 stationName；供同域复用） */
    AttendanceRuleVO toVO(AttendanceRule rule);
}
