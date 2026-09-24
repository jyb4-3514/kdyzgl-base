package com.qiujie.service.department.impl;

import com.qiujie.dto.department.DepartmentCreateRequest;
import com.qiujie.dto.department.DepartmentUpdateRequest;
import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.service.department.DepartmentService;
import com.qiujie.vo.department.DepartmentTreeVO;
import com.qiujie.vo.common.IdVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门服务实现（api.md 4.4）。
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;

    @Override
    public List<DepartmentTreeVO> tree() {
        // 全量活跃部门（< 200 行），一次载入内存组树；按 sort_order 升序、id 升序保证稳定排序
        List<Department> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSortOrder)
                        .orderByAsc(Department::getId));

        // 直属员工数（含禁用员工、不含已删除）
        Map<Long, Long> employeeCountMap = new HashMap<>();
        for (Map<String, Object> row : departmentMapper.countEmployeesByDept()) {
            Number deptId = (Number) row.get("dept_id");
            Number count = (Number) row.get("cnt");
            if (deptId != null && count != null) {
                employeeCountMap.put(deptId.longValue(), count.longValue());
            }
        }

        Map<Long, DepartmentTreeVO> nodeMap = new LinkedHashMap<>();
        for (Department department : departments) {
            DepartmentTreeVO node = new DepartmentTreeVO();
            node.setId(department.getId());
            node.setParentId(department.getParentId());
            node.setDeptName(department.getDeptName());
            node.setSortOrder(department.getSortOrder());
            node.setEmployeeCount(employeeCountMap.getOrDefault(department.getId(), 0L));
            nodeMap.put(department.getId(), node);
        }

        // 内存组树：parentId=0（或父节点缺失的异常数据）按根节点处理
        List<DepartmentTreeVO> roots = new ArrayList<>();
        for (DepartmentTreeVO node : nodeMap.values()) {
            DepartmentTreeVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (node.getParentId() == null || node.getParentId() == 0 || parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public IdVO create(DepartmentCreateRequest request) {
        // 父部门校验：0=根节点，否则须存在且未删除（3001）
        if (request.getParentId() == null || request.getParentId() != 0) {
            Department parent = departmentMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.DEPT_NOT_FOUND);
            }
        }
        checkDuplicateName(request.getParentId(), request.getDeptName(), null);

        Department department = new Department();
        department.setParentId(request.getParentId());
        department.setDeptName(request.getDeptName());
        department.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        departmentMapper.insert(department);
        return new IdVO(department.getId());
    }

    @Override
    public void update(Long id, DepartmentUpdateRequest request) {
        Department exist = departmentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        // 决策 D12：一期不允许修改父级，接口层直接拒绝该字段
        if (request.getParentId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许修改部门父级（父部门字段不可变更）");
        }
        // 同级重名校验（排除自身）
        checkDuplicateName(exist.getParentId(), request.getDeptName(), id);

        Department update = new Department();
        update.setId(id);
        update.setDeptName(request.getDeptName());
        update.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        departmentMapper.updateById(update);
    }

    @Override
    public void delete(Long id) {
        Department exist = departmentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        // 前置校验：有子部门拒删（3002）
        Long childCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.DEPT_HAS_CHILDREN);
        }
        // 前置校验：有归属员工拒删（3003）
        Long employeeCount = employeeMapper.selectCount(
                new LambdaQueryWrapper<Employee>().eq(Employee::getDeptId, id));
        if (employeeCount != null && employeeCount > 0) {
            throw new BusinessException(ErrorCode.DEPT_HAS_EMPLOYEES);
        }
        departmentMapper.deleteById(id); // 逻辑删除
    }

    /** 同级（同 parent_id）活跃部门重名校验；excludeId 用于编辑时排除自身 */
    private void checkDuplicateName(Long parentId, String deptName, Long excludeId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(Department::getParentId, parentId)
                .eq(Department::getDeptName, deptName);
        if (excludeId != null) {
            wrapper.ne(Department::getId, excludeId);
        }
        Long count = departmentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DEPT_NAME_EXISTS);
        }
    }
}
