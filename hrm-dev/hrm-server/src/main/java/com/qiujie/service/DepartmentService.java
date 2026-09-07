package com.qiujie.service;

import com.qiujie.dto.DepartmentCreateRequest;
import com.qiujie.dto.DepartmentUpdateRequest;
import com.qiujie.vo.DepartmentTreeVO;
import com.qiujie.vo.IdVO;

import java.util.List;

/**
 * 部门服务（api.md 4.4）。
 */
public interface DepartmentService {

    /** 部门树（含直属员工数，sort_order 升序） */
    List<DepartmentTreeVO> tree();

    /** 新增部门（3001/3004） */
    IdVO create(DepartmentCreateRequest request);

    /** 编辑部门：拒绝修改父级（400），同名同级查重（3004） */
    void update(Long id, DepartmentUpdateRequest request);

    /** 删除部门：有子部门（3002）/ 有员工（3003）拒删 */
    void delete(Long id);
}
