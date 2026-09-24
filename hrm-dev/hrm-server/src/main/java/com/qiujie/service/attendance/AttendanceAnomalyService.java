package com.qiujie.service.attendance;

import com.qiujie.service.attendance.support.AttendanceAnomalyDetector;

/**
 * 考勤异常检测服务（S4，内部预警能力）。
 * <p>
 * <b>接口承载待定</b>：Mock 的 {@code /attendance/summary} 与 {@code /attendance/detail} 出参均无异常字段，
 * 契约禁止新增接口/字段，故本能力暂以内部服务形态存在（供后续定时任务或管理端预警清单调用）。
 * TODO(扩展): 待 {@code api.md} 定义异常清单端点或出参字段后，由记录服务/控制器接入本服务；
 *   接入前保持「只读计算、不产生扣款」——扣款口径见算法 Q6（连缺阈值），未裁定不得联动计费。
 */
public interface AttendanceAnomalyService {

    /** 检测某驿站观察窗口内的异常员工（样本不足或 MAD=0 → 返回空集） */
    AttendanceAnomalyDetector.Report detect(Long stationId, Integer windowDays);
}
