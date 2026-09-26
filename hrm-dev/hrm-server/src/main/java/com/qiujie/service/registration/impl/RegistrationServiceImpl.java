package com.qiujie.service.registration.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.RegistrationProperties;
import com.qiujie.dto.registration.RegistrationSubmitRequest;
import com.qiujie.entity.EmployeeRegistration;
import com.qiujie.entity.HrFlow;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeRegistrationMapper;
import com.qiujie.mapper.HrFlowMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.AuthService;
import com.qiujie.service.hr.HrFlowService;
import com.qiujie.service.hr.support.HrConstants;
import com.qiujie.service.registration.RegistrationService;
import com.qiujie.service.registration.support.RegistrationConstants;
import com.qiujie.service.registration.support.RegistrationRetentionPolicy;
import com.qiujie.service.registration.support.RegistrationThrottle;
import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.PasswordUtil;
import com.qiujie.vo.registration.RegistrationDetailVO;
import com.qiujie.vo.registration.RegistrationSubmitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 员工自助注册服务实现（B3，registration-design §2/§3）。
 * <p>
 * 关键口径：
 * <ul>
 *   <li><b>M-2 恒定</b>：提交段<b>不判定「是否已注册」</b>，一律正常建单（受理外观恒定，无 9310/2003）；</li>
 *   <li><b>M-3 白名单</b>：仅落 §11.5 表 A 的申请侧字段，审批侧字段编译期不可达；</li>
 *   <li><b>凭据卫生</b>：密码仅 BCrypt 留痕，进入任一终态同事务置 NULL；{@code query_token_hash} 恒不写入；</li>
 *   <li><b>单事务</b>：申请单 + 入职审批单 + 回填 {@code flow_id} 全成功或全回滚。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final DateTimeFormatter APPLY_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String APPLY_NO_PREFIX = "RG-";
    private static final String EXPIRED_OPERATOR = "系统";

    private final EmployeeRegistrationMapper registrationMapper;
    private final HrFlowMapper hrFlowMapper;
    private final StationMapper stationMapper;
    private final AuthService authService;
    private final HrFlowService hrFlowService;
    private final RegistrationProperties properties;
    private final RegistrationThrottle throttle;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegistrationSubmitVO submit(RegistrationSubmitRequest request, String clientIp) {
        String phone = trim(request.getPhone());

        // 1) 注册验证码（REGISTER 场景，校验成功一次性作废；失败 1102/1103）
        authService.verifySceneCode(SmsScene.REGISTER, phone, trim(request.getSmsCode()));

        // 2) 同号「提交」日上限（S-2/M-7）；与发码频控互补，阻断无成本刷单
        throttle.checkSubmitAllowed(phone);

        // 3) 重复提交：同号存在 SUBMITTED → 9307（不动「是否已注册」判定，M-2）
        if (existsSubmittedByPhone(phone)) {
            throw new BusinessException(ErrorCode.REGISTRATION_DUPLICATE);
        }

        // 4) 意向驿站：存在 + 启用（意向阶段即校验，防落库无效引用）
        Long intentStationId = request.getIntentStationId();
        Station station = intentStationId == null ? null : stationMapper.selectById(intentStationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        if (station.getStatus() == null || station.getStatus() != 1) {
            throw new BusinessException(ErrorCode.STATION_DISABLED);
        }

        // 5) 建申请单：先以占位编号插入取 id，再回填正式编号（对齐 flow_no 两段式；apply_no 有 UNIQUE，占位须唯一）
        EmployeeRegistration registration = new EmployeeRegistration();
        registration.setApplyNo("TMP-" + System.nanoTime());
        registration.setRealName(trim(request.getRealName()));
        registration.setPhone(phone);
        String rawPassword = trim(request.getPassword());
        // U-02 定稿：注册密码仅作合规留痕、非员工口令；不填则不落散列
        registration.setPasswordHash(rawPassword == null || rawPassword.isBlank() ? null : PasswordUtil.hash(rawPassword));
        registration.setApplyStationId(intentStationId);
        registration.setApplyPosition(blankToNull(request.getIntentPosition()));
        registration.setSource(RegistrationConstants.SOURCE_STAFF_H5);
        registration.setAgreementVersion(trim(request.getAgreementVersion()));
        registration.setStatus(RegistrationConstants.STATUS_SUBMITTED);
        registration.setExpireTime(LocalDateTime.now().plusDays(properties.getExpireDays()));
        registration.setClientIp(blankToNull(clientIp));
        // query_token_hash 一期不启用 → 恒不写入
        registrationMapper.insert(registration);
        registration.setApplyNo(buildApplyNo(registration.getId()));
        registrationMapper.updateById(registration);

        // 6) 建入职审批单（source=SELF_REGISTER，operator 为空）+ 回填 flow_id（同一事务）
        Long flowId = hrFlowService.createSelfRegisterOnboarding(registration.getRealName(), phone,
                intentStationId, registration.getApplyPosition());
        registration.setFlowId(flowId);
        registrationMapper.updateById(registration);

        log.info("注册申请已提交：applyNo={}, phone={}", registration.getApplyNo(),
                DesensitizeUtil.maskPhone(phone));
        return toSubmitVO(registration);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegistrationDetailVO detail(String applyNo) {
        String no = trim(applyNo);
        if (no == null || no.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "申请编号不能为空");
        }
        EmployeeRegistration registration = registrationMapper.selectOne(
                new LambdaQueryWrapper<EmployeeRegistration>().eq(EmployeeRegistration::getApplyNo, no));
        if (registration == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入职申请不存在");
        }
        // 懒性超时判定（§1.3）：查询即判，命中则两表同置终态
        if (RegistrationConstants.STATUS_SUBMITTED.equals(registration.getStatus())
                && RegistrationRetentionPolicy.isExpired(registration.getExpireTime(), LocalDateTime.now())) {
            expireRegistration(registration);
        }
        return toDetailVO(registration);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        int handled = 0;

        // ① 超期未审 → 置终态（EXPIRED）并同步 hr_flow → REJECTED
        List<EmployeeRegistration> overdue = registrationMapper.selectList(
                new LambdaQueryWrapper<EmployeeRegistration>()
                        .eq(EmployeeRegistration::getStatus, RegistrationConstants.STATUS_SUBMITTED)
                        .isNotNull(EmployeeRegistration::getExpireTime)
                        .lt(EmployeeRegistration::getExpireTime, now));
        for (EmployeeRegistration registration : overdue) {
            expireRegistration(registration);
            handled++;
        }

        // ② 已拒绝/已失效超留存期 → 移除（逻辑删除，M-8）
        LocalDateTime cutoff = RegistrationRetentionPolicy.cleanupCutoff(now, properties.getRetentionDays());
        if (cutoff != null) {
            List<EmployeeRegistration> stale = registrationMapper.selectList(
                    new LambdaQueryWrapper<EmployeeRegistration>()
                            .in(EmployeeRegistration::getStatus,
                                    RegistrationConstants.STATUS_REJECTED, RegistrationConstants.STATUS_EXPIRED)
                            .lt(EmployeeRegistration::getUpdateTime, cutoff));
            for (EmployeeRegistration registration : stale) {
                registrationMapper.deleteById(registration.getId());
                handled++;
            }
        }
        if (handled > 0) {
            log.info("注册数据清理完成，本轮处理 {} 条（超期转终态 + 超留存期移除）", handled);
        }
        return handled;
    }

    // ==================== 私有：状态流转与凭据 ====================

    /**
     * 置 EXPIRED 终态并同步流程：<b>固定加锁顺序 hr_flow → registration</b>（§11.7）；
     * 进入终态同事务清空凭据散列。
     */
    private void expireRegistration(EmployeeRegistration registration) {
        HrFlow flow = registration.getFlowId() == null ? null
                : hrFlowMapper.selectByIdForUpdate(registration.getFlowId());
        registration.setStatus(RegistrationConstants.STATUS_EXPIRED);
        registration.setRejectReason(RegistrationConstants.REASON_EXPIRED);
        clearCredentials(registration);
        registrationMapper.updateById(registration);
        if (flow != null && HrConstants.FLOW_STATUS_IN_PROGRESS.equals(flow.getStatus())) {
            flow.setStatus(HrConstants.FLOW_STATUS_REJECTED);
            flow.setRejectReason(RegistrationConstants.REASON_EXPIRED);
            flow.setRejectedBy(EXPIRED_OPERATOR);
            flow.setRejectedTime(LocalDateTime.now());
            hrFlowMapper.updateById(flow);
        }
    }

    /** 清空凭据列（终态卫生）：密码散列与查询凭据一律置 NULL */
    private void clearCredentials(EmployeeRegistration registration) {
        registration.setPasswordHash(null);
        registration.setQueryTokenHash(null);
    }

    private boolean existsSubmittedByPhone(String phone) {
        return registrationMapper.selectCount(new LambdaQueryWrapper<EmployeeRegistration>()
                .eq(EmployeeRegistration::getPhone, phone)
                .eq(EmployeeRegistration::getStatus, RegistrationConstants.STATUS_SUBMITTED)) > 0;
    }

    // ==================== 私有：转换 ====================

    private RegistrationSubmitVO toSubmitVO(EmployeeRegistration registration) {
        RegistrationSubmitVO vo = new RegistrationSubmitVO();
        vo.setApplyNo(registration.getApplyNo());
        vo.setStatus(registration.getStatus());
        vo.setCreateTime(registration.getCreateTime());
        return vo;
    }

    private RegistrationDetailVO toDetailVO(EmployeeRegistration registration) {
        RegistrationDetailVO vo = new RegistrationDetailVO();
        vo.setApplyNo(registration.getApplyNo());
        vo.setRealName(registration.getRealName());
        vo.setPhone(DesensitizeUtil.maskPhone(registration.getPhone()));
        vo.setIntentStationId(registration.getApplyStationId());
        vo.setStationName(stationName(registration.getApplyStationId()));
        vo.setIntentPosition(registration.getApplyPosition());
        vo.setStatus(registration.getStatus());
        vo.setStatusLabel(RegistrationConstants.statusLabel(registration.getStatus()));
        vo.setRejectReason(registration.getRejectReason());
        vo.setCreateTime(registration.getCreateTime());
        vo.setApproveTime(registration.getApproveTime());
        vo.setSource(registration.getSource());
        vo.setClientIp(DesensitizeUtil.maskIp(registration.getClientIp()));
        return vo;
    }

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }

    private String buildApplyNo(Long id) {
        return APPLY_NO_PREFIX + LocalDate.now().format(APPLY_NO_DATE) + "-" + String.format("%04d", id);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
