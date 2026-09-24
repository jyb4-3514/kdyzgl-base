package com.qiujie.service.leave;

import com.qiujie.common.PageResult;
import com.qiujie.dto.leave.LeaveApplyRequest;
import com.qiujie.dto.leave.LeaveApproveRequest;
import com.qiujie.dto.leave.LeaveMineQuery;
import com.qiujie.dto.leave.LeavePreviewRequest;
import com.qiujie.dto.leave.LeaveQuery;
import com.qiujie.dto.leave.LeaveRevokeRequest;
import com.qiujie.dto.leave.LeaveSettingRequest;
import com.qiujie.vo.leave.LeavePreviewVO;
import com.qiujie.vo.leave.LeaveSettingVO;
import com.qiujie.vo.leave.LeaveVO;

/**
 * 请假服务（M7，13 接口，api.md §7.1）。算法 S5（半天单元区间）。
 * <p>
 * 依赖方向恒为 {@code leave → attendance/finance/notification}（只读或事件，不产生环）：
 * 计薪天数复用排班只读端口，撤回判定复用账期锁只读端口，申请/审批结果走通知出口。
 */
public interface LeaveService {

    /** POST /leave 提交申请 */
    LeaveVO apply(LeaveApplyRequest request);

    /** POST /leave/preview 只算不落库的试算 */
    LeavePreviewVO preview(LeavePreviewRequest request);

    /** GET /leave/my 我的请假（数据以登录身份收口） */
    PageResult<LeaveVO> mine(LeaveMineQuery query);

    /** GET /leave/list 管理端列表（ADMIN 全域 / STATION_ADMIN 本站） */
    PageResult<LeaveVO> list(LeaveQuery query);

    /** GET /leave/settings 扣款开关（仅 ADMIN，见契约冲突登记） */
    LeaveSettingVO getSettings();

    /** PUT /leave/settings 保存扣款开关（仅 ADMIN） */
    LeaveSettingVO saveSettings(LeaveSettingRequest request);

    /** GET /leave/{id} 详情（可见范围：ADMIN 全量 / 站长本站 / 本人；越权 9605） */
    LeaveVO detail(Long id);

    /** PUT /leave/{id} 编辑（仅申请本人且待初审） */
    LeaveVO update(Long id, LeaveApplyRequest request);

    /** POST /leave/{id}/cancel 申请人撤销（两种待审态） */
    LeaveVO cancel(Long id);

    /** POST /leave/{id}/resubmit 修改重提（原单 REJECTED，生成新单带 originId） */
    LeaveVO resubmit(Long id, LeaveApplyRequest request);

    /** POST /leave/{id}/station-approve 站长初审 */
    LeaveVO stationApprove(Long id, LeaveApproveRequest request);

    /** POST /leave/{id}/final-approve 老板终审（通过时落计薪天数快照） */
    LeaveVO finalApprove(Long id, LeaveApproveRequest request);

    /** POST /leave/{id}/revoke 撤回已批单（仅 ADMIN + 账期锁校验） */
    LeaveVO revoke(Long id, LeaveRevokeRequest request);

    /**
     * 请假扣款开关读取（{@code leaveDeductEnabled}，默认 false）。
     * <p>
     * 供财务域（P6 {@code PayrollContextProvider} 的 {@code TODO(扩展)} 接入点）在本批次之后消费；
     * Q6 未裁定前保持默认 false，实现与 Mock 现状逐位等价。
     */
    boolean isLeaveDeductEnabled();
}
