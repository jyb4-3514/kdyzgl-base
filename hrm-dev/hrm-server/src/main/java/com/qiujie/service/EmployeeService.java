package com.qiujie.service;

import com.qiujie.common.PageResult;
import com.qiujie.dto.EmployeeCreateRequest;
import com.qiujie.dto.EmployeeQuery;
import com.qiujie.dto.EmployeeStatusRequest;
import com.qiujie.dto.EmployeeUpdateRequest;
import com.qiujie.dto.PasswordResetRequest;
import com.qiujie.vo.EmployeeVO;
import com.qiujie.vo.IdVO;
import com.qiujie.vo.ImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 员工服务（api.md 4.3）。
 */
public interface EmployeeService {

    /** 分页查询：keyword 三字段模糊、deptId 含子部门、stationId、status，create_time 倒序 */
    PageResult<EmployeeVO> page(EmployeeQuery query);

    /** 员工详情（404 语义） */
    EmployeeVO detail(Long id);

    /** 新增员工 */
    IdVO create(EmployeeCreateRequest request);

    /** 编辑员工（username 不可改；角色变更受 2001/2002 保护） */
    void update(Long id, EmployeeUpdateRequest request);

    /** 删除员工：逻辑删除 + 强制下线 */
    void delete(Long id);

    /** 启用/禁用：禁用即强制下线 */
    void changeStatus(Long id, EmployeeStatusRequest request);

    /** 重置密码：pwd_changed=0 + 强制下线 */
    void resetPassword(Long id, PasswordResetRequest request);

    /** 下载导入模板（表头 + 批注，无示例行） */
    void downloadTemplate(HttpServletResponse response);

    /** Excel 导入：整批校验、全过才入库（单事务）、行级错误一次性返回 */
    ImportResultVO importEmployees(MultipartFile file);

    /** Excel 导出：按筛选全量（不分页），手机号完整输出 */
    void export(EmployeeQuery query, HttpServletResponse response);
}
