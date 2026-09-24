package com.qiujie.controller.employee;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.employee.EmployeeCreateRequest;
import com.qiujie.dto.employee.EmployeeQuery;
import com.qiujie.dto.employee.EmployeeStatusRequest;
import com.qiujie.dto.employee.EmployeeUpdateRequest;
import com.qiujie.dto.employee.PasswordResetRequest;
import com.qiujie.service.employee.EmployeeService;
import com.qiujie.vo.employee.EmployeeVO;
import com.qiujie.vo.common.IdVO;
import com.qiujie.vo.employee.ImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 员工接口（api.md 4.3），全部仅 ADMIN。
 */
@RequireRoles({"ADMIN"})
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /** 4.3.1 分页查询（keyword/deptId 含子部门/stationId/status）；C-04：分页参数越界 → 400 */
    @GetMapping
    public Result<PageResult<EmployeeVO>> page(@Valid EmployeeQuery query) {
        return Result.ok(employeeService.page(query));
    }

    /** 4.3.2 员工详情 */
    @GetMapping("/{id}")
    public Result<EmployeeVO> detail(@PathVariable Long id) {
        return Result.ok(employeeService.detail(id));
    }

    /** 4.3.3 新增员工 */
    @PostMapping
    public Result<IdVO> create(@Valid @RequestBody EmployeeCreateRequest request) {
        return Result.ok(employeeService.create(request));
    }

    /** 4.3.4 编辑员工（username 不可修改） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        employeeService.update(id, request);
        return Result.ok();
    }

    /** 4.3.5 删除员工（逻辑删除 + 强制下线） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return Result.ok();
    }

    /** 4.3.6 启用/禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody EmployeeStatusRequest request) {
        employeeService.changeStatus(id, request);
        return Result.ok();
    }

    /** 4.3.7 重置密码 */
    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        employeeService.resetPassword(id, request);
        return Result.ok();
    }

    /** 4.3.8 下载导入模板（.xlsx 文件流） */
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) {
        employeeService.downloadTemplate(response);
    }

    /** 4.3.9 Excel 导入（multipart/form-data，file 字段） */
    @PostMapping("/import")
    public Result<ImportResultVO> importEmployees(@RequestParam("file") MultipartFile file) {
        return Result.ok(employeeService.importEmployees(file));
    }

    /** 4.3.10 Excel 导出（.xlsx 文件流，筛选条件同列表、不分页） */
    @GetMapping("/export")
    public void export(EmployeeQuery query, HttpServletResponse response) {
        employeeService.export(query, response);
    }
}
