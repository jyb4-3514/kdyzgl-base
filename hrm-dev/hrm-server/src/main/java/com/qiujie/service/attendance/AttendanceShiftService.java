package com.qiujie.service.attendance;

import com.qiujie.dto.attendance.AttendanceShiftRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.vo.attendance.AttendanceShiftVO;

import java.util.List;

/**
 * 班次服务（4 接口：GET shifts / POST shifts / PUT shifts/{id} / DELETE shifts/{id}）。
 * 供排班与打卡判定引用；删除前校验是否被排班引用。同时提供「未排班兜底班次」的组装（打卡页展示用）。
 */
public interface AttendanceShiftService {

    /** 按驿站列班次（按开始时间升序） */
    List<AttendanceShiftVO> list(Long stationId);

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
     * 未排班时的兜底班次：按规则派生工时合成（{@code id=null}、{@code shiftName=默认班次}），
     * 避免「今天没排班」直接把打卡判成失败（对齐 Mock {@code defaultShiftOf}）。
     */
    AttendanceShiftVO defaultShift(AttendanceRule rule);
}
