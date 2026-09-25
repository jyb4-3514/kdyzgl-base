package com.qiujie.service.auth.impl;

import com.qiujie.common.SessionInfo;
import com.qiujie.config.AuthProperties;
import com.qiujie.config.SmsProperties;
import com.qiujie.config.condition.ExternalAdapterConditions;
import com.qiujie.dto.auth.ChangePasswordRequest;
import com.qiujie.dto.auth.DeviceInfo;
import com.qiujie.dto.auth.DeviceVerifyRequest;
import com.qiujie.dto.auth.LoginRequest;
import com.qiujie.dto.auth.SmsLoginRequest;
import com.qiujie.dto.auth.SmsSendRequest;
import com.qiujie.entity.AuthTrustedDevice;
import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.entity.LoginLog;
import com.qiujie.entity.Station;
import com.qiujie.enums.ClientType;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.LoginLogMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.AuthService;
import com.qiujie.service.auth.support.AuthRequestContext;
import com.qiujie.service.auth.support.CaptchaStore;
import com.qiujie.service.auth.support.DeviceFingerprint;
import com.qiujie.service.auth.support.DeviceSnapshot;
import com.qiujie.service.auth.support.DeviceTicket;
import com.qiujie.service.auth.support.DeviceTicketStore;
import com.qiujie.service.auth.support.DeviceTokenCodec;
import com.qiujie.service.auth.support.ClientAdmissionPolicy;
import com.qiujie.service.auth.support.SmsCodeStore;
import com.qiujie.service.auth.support.TrustedDeviceRegistry;
import com.qiujie.service.support.sms.SmsCodeGenerator;
import com.qiujie.service.support.sms.SmsConfigGuard;
import com.qiujie.service.support.sms.SmsScene;
import com.qiujie.service.support.sms.SmsSendResult;
import com.qiujie.service.support.sms.SmsSender;
import com.qiujie.service.support.sms.SmsUniversalCodePolicy;
import com.qiujie.service.support.sms.SmsVerifyPolicy;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.SessionIdGenerator;
import com.qiujie.util.SessionUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.auth.CaptchaVO;
import com.qiujie.vo.auth.LoginEmployeeVO;
import com.qiujie.vo.auth.LoginVO;
import com.qiujie.vo.auth.MeVO;
import com.qiujie.vo.auth.SmsSendVO;
import com.qiujie.vo.auth.TrustedDeviceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 认证服务实现（api.md 4.1 / 第 3 章；M4 登录契约改造见 multi-client-architecture §4）。
 * <p>
 * 职责边界：
 * <ul>
 *   <li>既有 4 端点（login/logout/me/password）<b>出入参与错误码不变</b>，仅 login 追加可选能力；</li>
 *   <li>新增 A1/A2/B2/C1/C2/D1 与设备信任判定（信任态服务端持有，凭摘要匹配）；</li>
 *   <li>Redis 键、频控、验证码生命周期见 {@link SmsCodeStore}；票据见 {@link DeviceTicketStore}；
 *       受信设备落库见 {@link TrustedDeviceRegistry}。</li>
 * </ul>
 * <b>红线</b>：验证码明文绝不入日志 / 异常 / 响应体；设备令牌明文仅经 HttpOnly Cookie 下发一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 降级态占位图形码（1×1 透明 PNG；与前端 Mock 对齐，演示态默认不启用图形码） */
    private static final String CAPTCHA_PLACEHOLDER_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    /** 手机号格式：11 位、以 1 开头（与前端 Mock validate.isPhone 同口径） */
    private static final String PHONE_PATTERN = "^1\\d{10}$";

    private final EmployeeMapper employeeMapper;
    private final LoginLogMapper loginLogMapper;
    private final DepartmentMapper departmentMapper;
    private final StationMapper stationMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SessionUtil sessionUtil;
    /** 认证与会话强类型配置（hrm.auth.*） */
    private final AuthProperties authProperties;
    /** 短信通道配置（hrm.sms.*） */
    private final SmsProperties smsProperties;
    /** 短信发送端口（生产 Aliyun / 非生产降级 Logging，由装配条件决定） */
    private final SmsSender smsSender;
    private final SmsCodeStore smsCodeStore;
    private final DeviceTicketStore deviceTicketStore;
    private final TrustedDeviceRegistry trustedDeviceRegistry;
    private final DeviceFingerprint deviceFingerprint;
    private final CaptchaStore captchaStore;
    /** 运行环境（判定 prod profile，供测试环境万能验证码的「非生产」条件复用 SmsConfigGuard 口径） */
    private final Environment environment;

    // ==================== 一期 4 端点 ====================

    @Override
    public LoginVO login(LoginRequest request, AuthRequestContext ctx, String headerClientType, String headerDeviceId) {
        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, request.getUsername()));

        // 账号不存在与密码错误统一 1001（防账号探测）；失败均写登录日志
        if (employee == null) {
            recordLoginLog(request.getUsername(), null, false, "账号密码错误", ctx.loginIp(), ctx.userAgent());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            recordLoginLog(employee.getUsername(), employee.getId(), false, "账号密码错误", ctx.loginIp(), ctx.userAgent());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            recordLoginLog(employee.getUsername(), employee.getId(), false, "账号已禁用", ctx.loginIp(), ctx.userAgent());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 端准入（1110）：请求头 X-Client-Type 优先（新契约），请求体 clientType 回退（兼容旧前端）；缺省/未知/非法一律拒。
        // 为什么放在「密码与状态校验通过之后」：先验身份再判端权限，避免通过错误码差异探测账号是否存在。
        // 为什么不是安全边界：端类型来自客户端可伪造/可省略的自称，权限恒以会话中的真实 role 为准。
        if (!isEndAllowed(headerClientType, request.getClientType(), request.getEntryAs(), employee.getRole())) {
            recordLoginLog(employee.getUsername(), employee.getId(), false, "该账号无权登录此端", ctx.loginIp(), ctx.userAgent());
            throw new BusinessException(ErrorCode.LOGIN_CLIENT_NOT_ALLOWED);
        }

        String clientTypeText = resolveClientTypeText(headerClientType, request.getClientType(), request.getEntryAs());
        String deviceIdText = firstNonBlank(trim(request.getDevice() == null ? null : request.getDevice().getDeviceId()),
                trim(headerDeviceId));

        // 设备信任判定：仅当上报了设备（与前端 Mock 同口径——未上报设备不触发二次验证）
        if (authProperties.isDeviceTrustEnabled() && deviceIdText != null && !deviceIdText.isBlank()) {
            AuthTrustedDevice trusted = resolveTrustedDevice(employee, ctx);
            if (trusted == null) {
                // 新设备：密码通过但需短信二次验证（HTTP 200 + needDeviceVerify 分流，不签发会话）
                String ticket = deviceTicketStore.issue(employee.getId(), toDeviceSnapshot(request.getDevice()),
                        clientTypeText, request.getEntryAs(), nowEpochMillis());
                recordLoginLog(employee.getUsername(), employee.getId(), false, "新设备需短信二次验证",
                        ctx.loginIp(), ctx.userAgent());
                LoginVO vo = new LoginVO();
                vo.setEmployee(toLoginEmployeeVO(employee));
                vo.setNeedDeviceVerify(true);
                vo.setDeviceTrusted(false);
                vo.setTwoFactorTicket(ticket);
                return vo;
            }
            // 已受信：刷新最近活跃时间（失败不影响登录）
            safeTouch(trusted.getId());
        }

        return issueLoginSession(employee, clientTypeText, deviceIdText, ctx, "登录成功", true, null);
    }

    @Override
    public void logout() {
        // 多端会话：仅注销「当前会话」（sid = 当前 jti），不影响该员工其他端/设备会话
        String sid = UserContext.getJti();
        Long userId = UserContext.getUserId();
        if (sid != null && !sid.isBlank()) {
            // 幂等：会话不存在也返回成功
            sessionUtil.deleteBySid(sid);
        } else if (userId != null) {
            // 旧 token 回退：无 sid 时沿用旧键删除，语义与改造前一致
            sessionUtil.delete(userId);
        }
    }

    @Override
    public MeVO me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Employee employee = employeeMapper.selectById(userId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        MeVO vo = new MeVO();
        vo.setId(employee.getId());
        vo.setUsername(employee.getUsername());
        vo.setRealName(employee.getRealName());
        vo.setPhone(DesensitizeUtil.maskPhone(employee.getPhone()));
        vo.setGender(employee.getGender());
        vo.setRole(employee.getRole());
        vo.setDeptId(employee.getDeptId());
        vo.setStationId(employee.getStationId());
        vo.setEntryDate(employee.getEntryDate());
        vo.setPwdChanged(employee.getPwdChanged() != null && employee.getPwdChanged() == 1);
        vo.setLastLoginTime(employee.getLastLoginTime());
        if (employee.getDeptId() != null) {
            Department department = departmentMapper.selectById(employee.getDeptId());
            vo.setDeptName(department == null ? null : department.getDeptName());
        }
        if (employee.getStationId() != null) {
            Station station = stationMapper.selectById(employee.getStationId());
            vo.setStationName(station == null ? null : station.getStationName());
        }
        return vo;
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Employee employee = employeeMapper.selectById(userId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        // 原密码校验失败 → 1004（新密码强度已由 DTO 校验）
        if (!passwordEncoder.matches(request.getOldPassword(), employee.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        Employee update = new Employee();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        update.setPwdChanged(1);
        employeeMapper.updateById(update);
        // 改密后删除该员工全部会话（跨端全失效），旧 Token 立即失效（需重新登录）
        sessionUtil.deleteAllOfEmployee(userId);
        // 「改密即失效」：同时使其已信任设备全部失效，防止旧设备凭旧令牌免二次验证（安全加固 ①）
        revokeTrustedDevicesQuietly(userId);
    }

    // ==================== A1 短信下发 ====================

    @Override
    public SmsSendVO sendSms(SmsSendRequest request, AuthRequestContext ctx) {
        // 图形验证码：仅在开启时校验（默认关闭；演示态不启用）
        if (captchaStore.isEnabled() && !captchaStore.consume(request.getCaptchaTicket(), request.getCaptchaCode())) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
        SmsScene scene = resolveScene(request.getScene());
        if (scene == SmsScene.DEVICE_VERIFY) {
            return sendDeviceVerifyCode(request, ctx);
        }
        return sendLoginCode(request, ctx, scene);
    }

    /** LOGIN / PERIODIC_REAUTH 场景：按手机号定位账号并发码 */
    private SmsSendVO sendLoginCode(SmsSendRequest request, AuthRequestContext ctx, SmsScene scene) {
        String phone = trim(request.getPhone());
        if (phone == null || !phone.matches(PHONE_PATTERN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入正确的 11 位手机号");
        }
        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getPhone, phone));
        // 账号不存在与未绑手机号统一 1109，避免以不同码暴露手机号是否已注册（防枚举）
        if (employee == null) {
            throw new BusinessException(ErrorCode.PHONE_NOT_BOUND);
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        long now = nowEpochSeconds();
        if (smsCodeStore.isSendBlocked(phone, ctx.loginIp(), trim(request.getDeviceId()), employee.getId(), now)) {
            throw new BusinessException(ErrorCode.SMS_RATE_LIMITED);
        }
        dispatchCode(employee.getPhone(), scene, phone, employee.getId(), ctx, trim(request.getDeviceId()), now);
        return buildSendVO();
    }

    /** DEVICE_VERIFY 场景：按二次验证票据定位员工并发码（页面手机号脱敏只读，前端不回传明文） */
    private SmsSendVO sendDeviceVerifyCode(SmsSendRequest request, AuthRequestContext ctx) {
        DeviceTicket ticket = requireTicket(request.getTwoFactorTicket());
        Employee employee = requireEnabledEmployee(ticket.getEmployeeId());
        // 端准入复判（票据签发时已过；此处防票据被跨端复用）——与登录共用同一策略对象
        if (!ClientAdmissionPolicy.isLoginAllowed(null, ticket.getClientType(), ticket.getEntryAs(),
                employee.getRole(), allowedRoles())) {
            throw new BusinessException(ErrorCode.LOGIN_CLIENT_NOT_ALLOWED);
        }
        if (employee.getPhone() == null || employee.getPhone().isBlank()) {
            throw new BusinessException(ErrorCode.PHONE_NOT_BOUND);
        }
        long now = nowEpochSeconds();
        String deviceId = firstNonBlank(trim(request.getDeviceId()), trim(ticket.getDeviceId()));
        if (smsCodeStore.isSendBlocked(employee.getPhone(), ctx.loginIp(), deviceId, employee.getId(), now)) {
            throw new BusinessException(ErrorCode.SMS_RATE_LIMITED);
        }
        dispatchCode(employee.getPhone(), SmsScene.DEVICE_VERIFY, String.valueOf(employee.getId()),
                employee.getId(), ctx, deviceId, now);
        return buildSendVO();
    }

    /** 生成验证码 → 投递 → 落 Redis → 记频控（顺序不可调换：投递失败不得留下可用验证码） */
    private void dispatchCode(String phone, SmsScene scene, String identifier, Long employeeId,
                              AuthRequestContext ctx, String deviceId, long nowEpochSeconds) {
        String code = newVerificationCode();
        SmsSendResult result = smsSender.send(phone, scene, code);
        if (!result.success()) {
            // 失败原因仅记录内部标识（不含验证码与上游原始报文）
            log.warn("短信发送失败，场景={}，通道={}，原因={}", scene.name(), result.provider(), result.failReason());
            throw new BusinessException(ErrorCode.SMS_UNAVAILABLE);
        }
        smsCodeStore.saveCode(scene, identifier, code);
        smsCodeStore.recordSend(phone, ctx.loginIp(), deviceId, employeeId, nowEpochSeconds);
    }

    /**
     * 生成验证码：降级通道（未配置厂商凭据）使用约定的开发固定码，便于联调；
     * 生产环境厂商凭据必配（{@code SmsConfigGuard} 启动期 fail-fast），故固定码不可能在生产生效。
     */
    private String newVerificationCode() {
        if (isDegradedChannel()) {
            String fixed = trim(smsProperties.getDevFixedCode());
            if (fixed != null && !fixed.isBlank()) {
                return fixed;
            }
        }
        return SmsCodeGenerator.generate(smsProperties.getCodeLength());
    }

    /** 是否处于降级通道（未配置有效阿里云短信凭据） */
    private boolean isDegradedChannel() {
        return !ExternalAdapterConditions.aliyunSmsConfigured(
                smsProperties.getProvider(),
                smsProperties.getAliyun() == null ? null : smsProperties.getAliyun().getAccessKeyId(),
                smsProperties.getAliyun() == null ? null : smsProperties.getAliyun().getAccessKeySecret());
    }

    // ==================== A2 短信登录 ====================

    @Override
    public LoginVO smsLogin(SmsLoginRequest request, AuthRequestContext ctx, String headerClientType) {
        String phone = trim(request.getPhone());
        if (phone == null || !phone.matches(PHONE_PATTERN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入正确的 11 位手机号");
        }
        String code = trim(request.getCode());
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入验证码");
        }
        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getPhone, phone));
        // 账号不存在与未绑手机号统一 1001（防手机号枚举，与密码通道同口径）
        if (employee == null) {
            recordLoginLog(phone, null, false, "手机号未绑定账号", ctx.loginIp(), ctx.userAgent());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        // 端准入：请求头优先、请求体回退（与密码路径共用同一策略对象）
        if (!isEndAllowed(headerClientType, request.getClientType(), request.getEntryAs(), employee.getRole())) {
            recordLoginLog(employee.getUsername(), employee.getId(), false, "该账号无权登录此端",
                    ctx.loginIp(), ctx.userAgent());
            throw new BusinessException(ErrorCode.LOGIN_CLIENT_NOT_ALLOWED);
        }
        verifySmsCode(SmsScene.LOGIN, phone, code);

        String clientTypeText = resolveClientTypeText(headerClientType, request.getClientType(), request.getEntryAs());
        String deviceToken = establishDeviceTrust(employee, toDeviceSnapshot(request.getDevice()), clientTypeText, ctx);
        String deviceIdText = trim(request.getDevice() == null ? null : request.getDevice().getDeviceId());
        return issueLoginSession(employee, clientTypeText, deviceIdText, ctx, "短信验证码登录", false, deviceToken);
    }

    // ==================== B2 新设备二次验证 ====================

    @Override
    public LoginVO deviceVerify(DeviceVerifyRequest request, AuthRequestContext ctx) {
        String code = trim(request.getCode());
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入验证码");
        }
        DeviceTicket ticket = requireTicket(request.getTwoFactorTicket());
        Employee employee = requireEnabledEmployee(ticket.getEmployeeId());
        if (!ClientAdmissionPolicy.isLoginAllowed(null, ticket.getClientType(), ticket.getEntryAs(),
                employee.getRole(), allowedRoles())) {
            throw new BusinessException(ErrorCode.LOGIN_CLIENT_NOT_ALLOWED);
        }
        // 设备场景验证码按员工暂存（页面不回传明文手机号）
        verifySmsCode(SmsScene.DEVICE_VERIFY, String.valueOf(employee.getId()), code);
        // 票据一次性：校验通过即删
        deviceTicketStore.delete(trim(request.getTwoFactorTicket()));

        DeviceSnapshot snapshot = new DeviceSnapshot(trim(ticket.getDeviceId()), trim(ticket.getPlatform()),
                trim(ticket.getModel()), trim(ticket.getOsVersion()), trim(ticket.getAppVersion()));
        String deviceToken = establishDeviceTrust(employee, snapshot, ticket.getClientType(), ctx);
        return issueLoginSession(employee, ticket.getClientType(), trim(ticket.getDeviceId()), ctx,
                "设备二次验证通过", false, deviceToken);
    }

    // ==================== C1 / C2 受信设备 ====================

    @Override
    public List<TrustedDeviceVO> listDevices() {
        Long userId = requireLoginUserId();
        // 越权前提：只查本人设备（employeeId 来自会话，非入参）
        List<AuthTrustedDevice> rows = trustedDeviceRegistry.listActiveByEmployee(userId);
        String currentDeviceId = UserContext.get() == null ? null : trim(UserContext.get().getDeviceId());
        List<TrustedDeviceVO> list = new ArrayList<>(rows.size());
        for (AuthTrustedDevice row : rows) {
            TrustedDeviceVO vo = new TrustedDeviceVO();
            vo.setDeviceId(row.getDeviceId());
            vo.setPlatform(row.getPlatform());
            vo.setModel(row.getModel());
            vo.setLastIp(DesensitizeUtil.maskIp(row.getLastIp()));
            vo.setLastSeenTime(row.getLastSeenTime() == null ? null : row.getLastSeenTime().format(TIME_FORMATTER));
            vo.setCurrent(currentDeviceId != null && currentDeviceId.equals(trim(row.getDeviceId())));
            list.add(vo);
        }
        return list;
    }

    @Override
    public void revokeDevice(String deviceId) {
        Long userId = requireLoginUserId();
        String target = trim(deviceId);
        if (target == null || target.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少设备标识");
        }
        boolean revoked = trustedDeviceRegistry.revokeByDeviceId(userId, target, LocalDateTime.now());
        if (!revoked) {
            // 无匹配或已撤销：与前端 Mock 同码（1107），避免暴露「该设备是否属于本人」
            throw new BusinessException(ErrorCode.DEVICE_REVOKED);
        }
        // 撤销的是当前设备 → 同步结束当前会话（精准吊销，等效登出）
        String currentDeviceId = UserContext.get() == null ? null : trim(UserContext.get().getDeviceId());
        if (target.equals(currentDeviceId)) {
            String sid = UserContext.getJti();
            if (sid != null && !sid.isBlank()) {
                sessionUtil.deleteBySid(sid);
            }
        }
    }

    // ==================== D1 图形验证码 ====================

    @Override
    public CaptchaVO captcha() {
        CaptchaVO vo = new CaptchaVO();
        vo.setTicket(captchaStore.issue());
        // TODO(扩展): 生产启用图形码时替换为真实渲染图（当前为 1×1 透明 PNG 占位，与前端 Mock 对齐）
        vo.setImageBase64(CAPTCHA_PLACEHOLDER_BASE64);
        vo.setExpireIn(captchaStore.expireInSeconds());
        return vo;
    }

    // ==================== 内部：会话签发 ====================

    /**
     * 签发会话并组装登录出参（三条成功路径共用：密码登录 / 短信登录 / 设备二次验证）。
     *
     * @param includeNeedDeviceVerify 是否显式下发 {@code needDeviceVerify=false}
     *                                （仅密码登录路径下发，与前端 Mock 出参形态逐字对齐）
     * @param deviceToken             本次新签发的设备令牌明文（无则 null）；仅经 Cookie 下发，不进响应体
     */
    private LoginVO issueLoginSession(Employee employee, String clientTypeText, String deviceIdText,
                                      AuthRequestContext ctx, String successReason,
                                      boolean includeNeedDeviceVerify, String deviceToken) {
        String sid = SessionIdGenerator.newSid();
        String token = jwtUtil.generate(employee.getId(), employee.getUsername(), employee.getRole(), sid);
        LocalDateTime now = LocalDateTime.now();
        SessionInfo sessionInfo = new SessionInfo(
                sid, employee.getUsername(), employee.getRole(),
                ctx.loginIp() == null ? "" : ctx.loginIp(), now.format(TIME_FORMATTER), toStationIdText(employee));
        sessionInfo.setSid(sid);
        sessionInfo.setEmployeeId(String.valueOf(employee.getId()));
        // 端类型为客户自称，仅归一后作会话维度/审计；权限恒以会话 role 为准（不信任端类型）
        sessionInfo.setClientType(clientTypeText);
        sessionInfo.setDeviceId(deviceIdText == null ? "" : deviceIdText);
        // 认证时刻（服务端单调时钟）：重认证窗口判定的权威依据（安全报告 §4.3 建议 ①）
        sessionInfo.setLoginEpochSeconds(now.atZone(ZoneId.systemDefault()).toEpochSecond());
        sessionUtil.saveBySid(sid, sessionInfo);

        // 更新最后登录时间（同步刷新内存对象，使登录出参的 lastLoginTime 为本次登录时间）
        Employee update = new Employee();
        update.setId(employee.getId());
        update.setLastLoginTime(now);
        employeeMapper.updateById(update);
        employee.setLastLoginTime(now);

        recordLoginLog(employee.getUsername(), employee.getId(), true, successReason, ctx.loginIp(), ctx.userAgent());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setExpiresIn(jwtUtil.getExpireSeconds());
        vo.setEmployee(toLoginEmployeeVO(employee));
        vo.setDeviceTrusted(true);
        if (includeNeedDeviceVerify) {
            vo.setNeedDeviceVerify(false);
        }
        vo.setSessionExpireAt(now.plusSeconds(Math.max(0L, authProperties.getSessionTtlSeconds()))
                .format(TIME_FORMATTER));
        vo.setDeviceToken(deviceToken);
        return vo;
    }

    // ==================== 内部：设备信任 ====================

    /** 依据请求携带的设备令牌（HttpOnly Cookie）判定当前设备是否受信（信任放行唯一依据） */
    private AuthTrustedDevice resolveTrustedDevice(Employee employee, AuthRequestContext ctx) {
        String tokenHash = DeviceTokenCodec.sha256Hex(ctx.incomingDeviceToken());
        if (tokenHash == null) {
            return null;
        }
        return trustedDeviceRegistry.findUsableByToken(employee.getId(), tokenHash, nowEpochSeconds());
    }

    /**
     * 建立设备信任（短信登录 / 设备二次验证成功后调用）：
     * 服务端签发新令牌 → 只存摘要 → 返回明文给 Controller 写 HttpOnly Cookie。
     *
     * @return 新令牌明文；无设备信息 / 设备信任关闭时返回 {@code null}（不登记）
     */
    private String establishDeviceTrust(Employee employee, DeviceSnapshot snapshot, String clientTypeText,
                                        AuthRequestContext ctx) {
        if (!authProperties.isDeviceTrustEnabled() || snapshot == null
                || snapshot.deviceId() == null || snapshot.deviceId().isBlank()) {
            return null;
        }
        String platform = platformOf(snapshot.platform(), clientTypeText);
        String deviceToken = DeviceTokenCodec.newToken();
        String fingerprint = deviceFingerprint.compute(
                truncate(snapshot.deviceId(), 64), platform, truncate(snapshot.model(), 64),
                truncate(snapshot.osVersion(), 32), ctx.userAgent());
        trustedDeviceRegistry.upsertTrusted(
                employee.getId(), fingerprint, DeviceTokenCodec.sha256Hex(deviceToken),
                truncate(snapshot.deviceId(), 64), platform,
                truncate(snapshot.model(), 64), truncate(snapshot.osVersion(), 32), truncate(snapshot.appVersion(), 32),
                truncate(ctx.loginIp(), 45), LocalDateTime.now(),
                authProperties.getDeviceTrustTtlSeconds(), authProperties.getMaxTrustedDevicesPerEmployee());
        return deviceToken;
    }

    /** 使其全部受信设备失效（安全事件路径）；失败仅记日志，不回滚主流程 */
    private void revokeTrustedDevicesQuietly(Long employeeId) {
        try {
            trustedDeviceRegistry.revokeAllOfEmployee(employeeId);
        } catch (Exception e) {
            log.error("使受信设备失效失败，employeeId={}", employeeId, e);
        }
    }

    private void safeTouch(Long deviceRowId) {
        try {
            trustedDeviceRegistry.touch(deviceRowId, LocalDateTime.now());
        } catch (Exception e) {
            log.error("刷新受信设备活跃时间失败，deviceRowId={}", deviceRowId, e);
        }
    }

    // ==================== 内部：通用 ====================

    /**
     * 端准入（1110）：请求头 X-Client-Type 优先（新契约），请求体 clientType 回退（兼容旧前端）。
     * 三条登录路径与票据复判共用 {@link ClientAdmissionPolicy} 同一策略对象。
     */
    private boolean isEndAllowed(String headerClientType, String bodyClientType, String entryAs, String role) {
        return ClientAdmissionPolicy.isLoginAllowed(headerClientType, bodyClientType, entryAs, role, allowedRoles());
    }

    /** 组装端准入的三端允许角色集合（全部外置可配；缺失即该端 fail-closed） */
    private ClientAdmissionPolicy.AllowedRoles allowedRoles() {
        return new ClientAdmissionPolicy.AllowedRoles(authProperties.getPcAllowedRoles(),
                authProperties.getBossAllowedRoles(), authProperties.getStaffAllowedRoles());
    }

    /**
     * 会话中记录的端类型文本：与端准入同一归一函数产出的规范名（ADMIN/BOSS/STAFF/WEB）。
     * <p>注意：旧实现记录请求体原值（WEB/H5）；本次统一为规范端名，与 {@code SessionInfo.clientType} 的既有约定一致。
     * 端准入已通过时结果必非 null，兜底 WEB 仅为防御（正常不可达）。
     */
    private String resolveClientTypeText(String headerClientType, String bodyClientType, String entryAs) {
        ClientType end = ClientAdmissionPolicy.resolveEnd(headerClientType, bodyClientType, entryAs);
        return (end == null ? ClientType.WEB : end).name();
    }

    /** 设备平台归一：优先前端上报，其次按端类型（移动端 BOSS/STAFF → H5，其余 → WEB）归类 */
    private String platformOf(String platform, String clientTypeText) {
        String value = trim(platform);
        if (value != null && !value.isBlank()) {
            return truncate(value.toUpperCase(Locale.ROOT), 16);
        }
        ClientType end = ClientAdmissionPolicy.resolveEnd(null, clientTypeText, null);
        return ClientAdmissionPolicy.isMobileEnd(end) ? ClientAdmissionPolicy.CLIENT_H5 : ClientType.WEB.name();
    }

    private DeviceSnapshot toDeviceSnapshot(DeviceInfo device) {
        if (device == null) {
            return null;
        }
        return new DeviceSnapshot(trim(device.getDeviceId()), trim(device.getPlatform()), trim(device.getModel()),
                trim(device.getOsVersion()), trim(device.getAppVersion()));
    }

    /** 短信场景归一：空白/未知一律按 LOGIN（未知值不阻断登录，与前端 Mock 缺省语义一致） */
    private SmsScene resolveScene(String raw) {
        String value = trim(raw);
        if (value == null || value.isBlank()) {
            return SmsScene.LOGIN;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        for (SmsScene scene : SmsScene.values()) {
            if (scene.name().equals(upper)) {
                return scene;
            }
        }
        log.warn("未知短信场景，按 LOGIN 处理：{}", raw);
        return SmsScene.LOGIN;
    }

    /** 读取并校验二次验证票据（失效 → 1107 + 明确文案，与前端 Mock 一致） */
    private DeviceTicket requireTicket(String rawTicket) {
        DeviceTicket ticket = deviceTicketStore.find(trim(rawTicket), nowEpochMillis());
        if (ticket == null) {
            throw new BusinessException(ErrorCode.DEVICE_REVOKED, "设备验证已失效，请重新登录");
        }
        return ticket;
    }

    private Employee requireEnabledEmployee(Long employeeId) {
        Employee employee = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (employee == null || employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return employee;
    }

    /**
     * 校验并消费验证码（一次性）。
     * <p>判定口径与前端 Mock {@code verifyCode} 逐条一致：未申请/已过期 → 1102；达尝试上限 → 1103 并作废；
     * 比对失败 → 计数 +1（达上限则 1103 并作废，否则 1102）；通过 → 立即删除验证码。
     * <p><b>测试环境万能验证码短路</b>（{@code hrm.sms.dev-universal-code}）：三条件同时满足即直接返回，
     * <b>跳过</b> Redis 取码 / 比对 / 尝试计数读写 / 码作废（<b>不消耗</b>任何验证码与尝试次数）；
     * <b>不跳过</b>调用方在进入本方法前的入参与授权判定，以及本方法返回后的登录链路
     * （设备信任签发、会话建立）。生产环境该分支恒不生效（{@link SmsConfigGuard} 启动期 fail-fast 双保险）。
     */
    private void verifySmsCode(SmsScene scene, String identifier, String code) {
        if (SmsUniversalCodePolicy.isActive(smsProperties.getDevUniversalCode(),
                SmsConfigGuard.isProd(environment), code)) {
            // 仅记录场景，不回显码值 / 手机号 / 员工标识（验证码红线）
            log.warn("测试环境万能验证码校验通过（未读取、未消耗验证码），场景={}", scene.name());
            return;
        }
        String expected = smsCodeStore.getCode(scene, identifier);
        if (expected == null) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }
        int attempts = smsCodeStore.attempts(scene, identifier);
        SmsVerifyPolicy.Decision decision = SmsVerifyPolicy.verify(
                expected, code, attempts, smsProperties.getMaxVerifyAttempts());
        if (decision == SmsVerifyPolicy.Decision.OK) {
            smsCodeStore.clearCode(scene, identifier);
            return;
        }
        if (decision == SmsVerifyPolicy.Decision.ATTEMPTS_EXCEEDED) {
            smsCodeStore.clearCode(scene, identifier);
            throw new BusinessException(ErrorCode.SMS_CODE_ATTEMPTS_EXCEEDED);
        }
        int updated = smsCodeStore.incrementAttempts(scene, identifier);
        if (smsProperties.getMaxVerifyAttempts() > 0 && updated >= smsProperties.getMaxVerifyAttempts()) {
            smsCodeStore.clearCode(scene, identifier);
            throw new BusinessException(ErrorCode.SMS_CODE_ATTEMPTS_EXCEEDED);
        }
        throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
    }

    private SmsSendVO buildSendVO() {
        SmsSendVO vo = new SmsSendVO();
        vo.setSent(true);
        vo.setExpireIn(smsCodeStore.codeExpireInSeconds());
        vo.setNextAllowedIn(smsCodeStore.nextAllowedInSeconds());
        vo.setRequireCaptcha(captchaStore.isEnabled());
        return vo;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private long nowEpochMillis() {
        return System.currentTimeMillis();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** 弱信号截断至列宽（弱信号不因超长而拒绝登录，只截断；列宽取自 V15 定义） */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 归属驿站 → 会话存储文本：null 归一为 ""（「已解析且无归属」，与旧会话的 null「字段缺失」区分） */
    private String toStationIdText(Employee employee) {
        return employee.getStationId() == null ? "" : String.valueOf(employee.getStationId());
    }

    /** 员工实体 → 登录出参 VO（C-06：与 EmployeeVO / Mock toEmployeeVO 同构，字段只增不减） */
    private LoginEmployeeVO toLoginEmployeeVO(Employee employee) {
        LoginEmployeeVO vo = new LoginEmployeeVO();
        vo.setId(employee.getId());
        vo.setUsername(employee.getUsername());
        vo.setRealName(employee.getRealName());
        vo.setPhone(DesensitizeUtil.maskPhone(employee.getPhone()));
        vo.setRole(employee.getRole());
        // pwd_changed=0 时前端强制进入改密流程（requirement.md 5.3）
        vo.setPwdChanged(employee.getPwdChanged() != null && employee.getPwdChanged() == 1);
        vo.setGender(employee.getGender());
        vo.setDeptId(employee.getDeptId());
        vo.setStationId(employee.getStationId());
        vo.setStatus(employee.getStatus());
        vo.setEntryDate(employee.getEntryDate());
        vo.setRemark(employee.getRemark());
        vo.setLastLoginTime(employee.getLastLoginTime());
        vo.setCreateTime(employee.getCreateTime());
        if (employee.getDeptId() != null) {
            Department department = departmentMapper.selectById(employee.getDeptId());
            vo.setDeptName(department == null ? null : department.getDeptName());
        }
        if (employee.getStationId() != null) {
            Station station = stationMapper.selectById(employee.getStationId());
            vo.setStationName(station == null ? null : station.getStationName());
        }
        return vo;
    }

    /**
     * 写登录日志（成功/失败均记录，含 IP 与 UA）。
     * 审计数据写入失败不阻断登录主流程，仅记错误日志。
     */
    private void recordLoginLog(String username, Long employeeId, boolean success,
                                String failReason, String loginIp, String userAgent) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUsername(username);
            loginLog.setEmployeeId(employeeId);
            loginLog.setLoginIp(loginIp == null ? "" : loginIp);
            loginLog.setLoginResult(success ? 1 : 0);
            loginLog.setFailReason(failReason);
            // UA 截断至 255（表字段长度）
            loginLog.setUserAgent(userAgent == null ? null
                    : userAgent.substring(0, Math.min(userAgent.length(), 255)));
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("写入登录日志失败，username={}", username, e);
        }
    }
}
