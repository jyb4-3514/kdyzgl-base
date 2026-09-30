package com.qiujie.service.finance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.PayrollScheduleProperties;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollRun;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.notification.NotificationService;
import com.qiujie.service.support.ClientLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 薪资自动化站内信投递收口（B4a）：类型 7/8/9 与运行失败告警 10。
 * <p>
 * <b>为什么独立成组件</b>：类型 7/9 推管理员、8 推员工，落点分散在调度侧（{@link com.qiujie.service.finance.port.PayrollRunNotifier}）
 * 与工资单服务（{@code publish/objection}）；若各写各的「解析管理员 + try/catch + NOTIFY_SKIP」必然漂移。
 * 本类统筹：① 管理员接收人单一真源（{@code NotificationService#findAdminEmployeeIds}）；
 * ② 通知失败一律吞异常、只留 {@code NOTIFY_SKIP} 排障痕迹，<b>不阻断主流程</b>（安全 M-7）；
 * ③ 文案统一截断（防 {@code sendSystem} 超长抛错）。
 * <p>
 * <b>投递门槛</b>：类型 7 受该站 {@code station_payroll_setting.notify_enabled} 控制；失败告警受
 * {@code hrm.payroll.schedule.notify-on-fail} 控制；类型 8/9 由状态机保证只在发布/异议后触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollNotifySupport {

    /** 业务跳转类型（对齐 design §4.5 / {@code sendSystem} 契约） */
    public static final String BIZ_TYPE = "payroll";
    /** 类型 7：工资单待审核 → 管理员 */
    public static final int TYPE_PENDING_APPROVAL = 7;
    /** 类型 8：工资单已发布 → 员工本人 */
    public static final int TYPE_PUBLISHED = 8;
    /** 类型 9：工资单异议退回 → 管理员 */
    public static final int TYPE_OBJECTED = 9;
    /** 类型 10：自动算薪运行失败 / 僵死回收告警 → 管理员 */
    public static final int TYPE_RUN_FAIL_ALERT = 10;

    /** 排障留痕标记：通知未投递时写应用日志（不落库），与请假域口径一致 */
    public static final String NOTIFY_SKIP = "NOTIFY_SKIP";

    /** 内容长度上限（对齐 {@code sendSystem} 契约，超长截断防抛错中断业务） */
    private static final int CONTENT_MAX = 500;

    private final NotificationService notificationService;
    private final StationPayrollSettingMapper settingMapper;
    private final PayrollScheduleProperties scheduleProperties;

    /**
     * 类型 7：自动算薪成功生成（并自动提交）后推管理员。
     * <p>受该站 {@code notify_enabled} 控制；关闭或无接收人时留 {@code NOTIFY_SKIP}，不投递。
     */
    public void notifyPendingApproval(PayrollRun run, List<Long> payrollIds, int generatedCount) {
        if (run == null || run.getStationId() == null) {
            skip("待审核通知未投递：运行记录或驿站缺失");
            return;
        }
        if (!isNotifyEnabled(run.getStationId())) {
            skip("待审核通知未投递：驿站 notify_enabled=0（stationId=" + run.getStationId() + "）");
            return;
        }
        List<Long> payrollIdList = payrollIds == null ? List.of() : payrollIds;
        if (payrollIdList.isEmpty()) {
            skip("待审核通知未投递：本次无生成单据（stationId=" + run.getStationId() + "）");
            return;
        }
        List<Long> admins = adminIds();
        if (admins.isEmpty()) {
            skip("待审核通知未投递：无在职管理员接收人");
            return;
        }
        String title = "工资单待审核";
        String content = limit("驿站（ID=" + run.getStationId() + "）" + run.getTargetMonth()
                + " 自动生成 " + generatedCount + " 张工资单，待您审核");
        // biz_id=payroll.id：逐单推送，便于管理员点开对应工资单（对齐 design §4.5 跳转契约）
        for (Long payrollId : payrollIdList) {
            for (Long adminId : admins) {
                send(adminId, TYPE_PENDING_APPROVAL, title, content, payrollId);
            }
        }
    }

    /**
     * 类型 8：工资单已发布 → 员工本人（{@code biz_id=payroll.id}）。
     * <p>调用方须保证状态确已落 {@code PUBLISHED}（防草稿/待审推给员工）。
     */
    public void notifyPublished(Payroll payroll) {
        if (payroll == null || payroll.getEmployeeId() == null || payroll.getId() == null) {
            skip("已发布通知未投递：工资单或员工缺失");
            return;
        }
        String content = limit("您的 " + payroll.getMonth() + " 工资单已发布，请查看并确认");
        send(payroll.getEmployeeId(), TYPE_PUBLISHED, "工资单已发布", content, payroll.getId());
    }

    /** 类型 9：员工提异议退回后推管理员（{@code biz_id=payroll.id}） */
    public void notifyObjection(Payroll payroll) {
        if (payroll == null || payroll.getId() == null) {
            skip("异议通知未投递：工资单缺失");
            return;
        }
        List<Long> admins = adminIds();
        if (admins.isEmpty()) {
            skip("异议通知未投递：无在职管理员接收人");
            return;
        }
        String content = limit("员工（ID=" + payroll.getEmployeeId() + "）对 " + payroll.getMonth()
                + " 工资单提出异议，待处理：" + safe(payroll.getObjectionReason()));
        for (Long adminId : admins) {
            send(adminId, TYPE_OBJECTED, "工资单异议退回", content, payroll.getId());
        }
    }

    /**
     * 类型 10：自动算薪执行失败 / 僵死回收告警 → 管理员。
     * <p>受 {@code hrm.payroll.schedule.notify-on-fail} 控制；failure 原因已由上游脱敏构造。
     */
    public void notifyRunFailed(PayrollRun run, String failReason) {
        if (run == null) {
            skip("失败告警未投递：运行记录缺失");
            return;
        }
        if (!scheduleProperties.isNotifyOnFail()) {
            skip("失败告警未投递：notify-on-fail=false（stationId=" + run.getStationId() + "）");
            return;
        }
        List<Long> admins = adminIds();
        if (admins.isEmpty()) {
            skip("失败告警未投递：无在职管理员接收人");
            return;
        }
        String content = limit("驿站（ID=" + run.getStationId() + "）" + run.getTargetMonth()
                + " 自动算薪执行失败：" + safe(failReason));
        // 失败告警无单张工资单可跳转，biz_id 传 null（保留 biz_type 供薪资分组）
        for (Long adminId : admins) {
            send(adminId, TYPE_RUN_FAIL_ALERT, "自动算薪执行失败", content, null);
        }
    }

    // ==================== 私有 ====================

    /** 该站是否开启「生成后推管理员」；未配置视为关闭（保守，不投递） */
    private boolean isNotifyEnabled(Long stationId) {
        List<StationPayrollSetting> rows = settingMapper.selectList(new LambdaQueryWrapper<StationPayrollSetting>()
                .eq(StationPayrollSetting::getStationId, stationId));
        return !rows.isEmpty() && Integer.valueOf(1).equals(rows.get(0).getNotifyEnabled());
    }

    /** 管理员接收人单一真源；解析异常降级为空集（不阻断主流程） */
    private List<Long> adminIds() {
        try {
            List<Long> ids = notificationService.findAdminEmployeeIds();
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            log.warn("{} 管理员接收人解析失败", NOTIFY_SKIP, e);
            return List.of();
        }
    }

    /** 单条投递：失败只留痕不抛出（防 afterCommit / 业务事务内异常外溢） */
    private void send(Long employeeId, int type, String title, String content, Long payrollId) {
        try {
            notificationService.sendSystem(employeeId, type, title, content, BIZ_TYPE, payrollId);
        } catch (Exception e) {
            log.warn("{} 站内信投递失败：type={}, employeeId={}, bizId={}", NOTIFY_SKIP, type, employeeId, payrollId, e);
        }
    }

    private void skip(String reason) {
        log.warn("{} {}", NOTIFY_SKIP, reason);
    }

    /** 文本兜底：擦凭据形态串 + 截断（防超长触发 sendSystem 校验异常） */
    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return ClientLogSanitizer.truncate(ClientLogSanitizer.scrub(value.trim()), CONTENT_MAX);
    }

    private String limit(String content) {
        return ClientLogSanitizer.truncate(content, CONTENT_MAX);
    }
}
