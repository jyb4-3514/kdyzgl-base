package com.qiujie.service.hr;

import com.qiujie.common.PageResult;
import com.qiujie.dto.hr.HrEmployeeQuery;
import com.qiujie.dto.hr.HrProfileUpdateRequest;
import com.qiujie.dto.hr.HrSalaryUpdateRequest;
import com.qiujie.vo.hr.HrProfileDetailVO;
import com.qiujie.vo.hr.HrProfileVO;
import com.qiujie.vo.hr.HrSalaryDetailVO;
import com.qiujie.vo.hr.HrSalaryVO;

/**
 * 人事档案与定薪服务（6 接口：档案 3 / 定薪 3，api.md / Mock {@code routes/hr.js}，架构 §6.2 P5）。
 * <p>
 * 越权口径：档案详情、定薪详情按 Mock {@code canAccessEmployee}——ADMIN 全量，其余角色仅本人，越权 403。
 * 离职判定真源为 {@code hr_profile.leave_date}（非 {@code employee.status}）。
 */
public interface HrProfileService {

    /** 人事档案列表（仅 ADMIN；按员工筛选，仅返回已建档员工） */
    PageResult<HrProfileVO> listProfiles(HrEmployeeQuery query);

    /** 人事档案详情（本人或 ADMIN；不存在 9301，越权 403），含定薪摘要 */
    HrProfileDetailVO profileDetail(Long employeeId);

    /** 编辑人事档案（仅 ADMIN；白名单写入；不存在 9301，已离职 9302） */
    HrProfileVO updateProfile(Long employeeId, HrProfileUpdateRequest request);

    /** 定薪列表（仅 ADMIN；按员工筛选，仅返回已定薪员工） */
    PageResult<HrSalaryVO> listSalaries(HrEmployeeQuery query);

    /** 定薪详情（本人或 ADMIN；无档案 9305，越权 403），含调薪留痕 */
    HrSalaryDetailVO salaryDetail(Long employeeId);

    /** 保存定薪（仅 ADMIN）：覆盖当前档案 + 追加调薪留痕；无档案 9305，已离职 9302 */
    HrSalaryDetailVO updateSalary(Long employeeId, HrSalaryUpdateRequest request);
}
