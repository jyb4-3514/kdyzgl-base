package com.qiujie.service.registration;

import com.qiujie.dto.registration.RegistrationSubmitRequest;
import com.qiujie.vo.registration.RegistrationDetailVO;
import com.qiujie.vo.registration.RegistrationSubmitVO;

/**
 * 员工自助注册服务（B3，registration-design §3）。
 * <p>
 * <b>公开面仅 R-2 提交</b>（R-1 复用 {@code /auth/sms/send}）；R-3 详情 ADMIN-only；
 * 审批/驳回联动（R-6/R-9）落在人事域 {@code HrFlowService}（复用步骤机），本服务只承载「注册事实」。
 * <p>
 * 红线：不得采集/落库审批侧字段（role/薪资/部门等）；密码仅留痕且终态清散列；验证码/密码不入日志。
 */
public interface RegistrationService {

    /**
     * R-2 提交注册申请（公开，单事务：申请单 + 入职审批单 + 回填 flow_id）。
     *
     * @param request  提交入参（字段白名单，未知字段已在 DTO 层拒绝）
     * @param clientIp 提交来源 IP（审计留痕，出参脱敏）
     * @return 受理外观（对「是否已注册」恒定）
     */
    RegistrationSubmitVO submit(RegistrationSubmitRequest request, String clientIp);

    /** R-3 申请单详情（ADMIN-only，按申请编号；出参脱敏、不回传凭据） */
    RegistrationDetailVO detail(String applyNo);

    /**
     * 清理任务（M-8）：超期未审转终态 + 超留存期终态数据移除。
     * <p>可外置定时触发（{@code TODO(扩展)}）；本批提供可执行方法供运维/定时调用。
     *
     * @return 本轮处理的记录数
     */
    int cleanupExpired();
}
