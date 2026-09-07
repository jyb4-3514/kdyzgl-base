package com.qiujie.service;

import com.qiujie.dto.StationRequest;
import com.qiujie.vo.IdVO;
import com.qiujie.vo.StationVO;

import java.util.List;

/**
 * 驿站服务（api.md 4.5）。
 */
public interface StationService {

    /** 驿站列表（全量，可选状态过滤，含员工数、电话脱敏） */
    List<StationVO> list(Integer status);

    /** 新增驿站（code 格式与唯一校验） */
    IdVO create(StationRequest request);

    /** 编辑驿站（一期允许改 code，唯一校验生效） */
    void update(Long id, StationRequest request);

    /** 启用/停用 */
    void changeStatus(Long id, Integer status);

    /** 删除驿站（有归属员工拒删） */
    void delete(Long id);
}
