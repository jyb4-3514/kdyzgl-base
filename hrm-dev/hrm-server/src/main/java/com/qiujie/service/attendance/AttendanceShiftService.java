package com.qiujie.service.attendance;

import com.qiujie.dto.attendance.AttendanceShiftRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.vo.attendance.AttendanceShiftVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 班次服务（4 接口：GET shifts / POST shifts / PUT shifts/{id} / DELETE shifts/{id}）。
 * 供排班与打卡判定引用；删除前校验是否被排班引用。同时提供「未排班兜底班次」的组装（打卡页展示用）。
 * <p>
 * <b>真源统一（方案 v1.2）</b>：打卡时段由「该驿站启用班次」派生，故本服务另暴露启用班次的读取入口，
 * 供打卡/状态/补卡/规则出参统一复用（避免各处重复写「status=1 且未软删 + 按 start_time 升序」口径）。
 */
public interface AttendanceShiftService {

    /** 按驿站列班次（按开始时间升序） */
    List<AttendanceShiftVO> list(Long stationId);

    /**
     * 该驿站<b>启用</b>班次实体（{@code status=1} 且未软删），按 {@code start_time} 升序。
     * <p>打卡时段派生的唯一入口（真源）。
     */
    List<AttendanceShift> enabledShifts(Long stationId);

    /**
     * 批量：驿站 → 启用班次（按 {@code start_time} 升序）。供 {@code GET /rule/list} 批量预取，规避 N+1（方案 §10.4 RK-1）。
     */
    Map<Long, List<AttendanceShift>> enabledShiftsByStation(Collection<Long> stationIds);

    /** 新增班次（仅 ADMIN） */
    AttendanceShiftVO create(AttendanceShiftRequest request);

    /** 编辑班次（仅 ADMIN；不存在 → 404「班次不存在」） */
    AttendanceShiftVO update(Long id, AttendanceShiftRequest request);

    /** 删除班次（仅 ADMIN；被排班引用 → 400） */
    void delete(Long id);

    /** 班次实体（不存在返回 null；供排班校验复用） */
    AttendanceShift findEntity(Long id);

    /** 班次实体 → 出参 */
    AttendanceShiftVO toVO(AttendanceShift shift);

    /**
     * 未排班时的兜底班次：改为「该驿站<b>首个启用班次</b>」派生（{@code id=null}，字段取该班次快照）；
     * <b>无启用班次时返回 {@code null}</b>（方案 §5 / §8.4 / §10.7）。避免「今天没排班」直接把打卡判成失败。
     */
    AttendanceShiftVO defaultShift(AttendanceRule rule);
}
