package com.qiujie.controller.department;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.department.DepartmentCreateRequest;
import com.qiujie.dto.department.DepartmentUpdateRequest;
import com.qiujie.service.department.DepartmentService;
import com.qiujie.vo.department.DepartmentTreeVO;
import com.qiujie.vo.common.IdVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门接口（api.md 4.4），全部仅 ADMIN。
 */
@RequireRoles({"ADMIN"})
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /** 4.4.1 部门树（含直属员工数） */
    @GetMapping("/tree")
    public Result<List<DepartmentTreeVO>> tree() {
        return Result.ok(departmentService.tree());
    }

    /** 4.4.2 新增部门 */
    @PostMapping
    public Result<IdVO> create(@Valid @RequestBody DepartmentCreateRequest request) {
        return Result.ok(departmentService.create(request));
    }

    /** 4.4.3 编辑部门（不允许修改父级） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        departmentService.update(id, request);
        return Result.ok();
    }

    /** 4.4.4 删除部门（有子部门/有员工拒删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.ok();
    }
}
