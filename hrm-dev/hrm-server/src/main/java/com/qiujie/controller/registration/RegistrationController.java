package com.qiujie.controller.registration;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.registration.RegistrationSubmitRequest;
import com.qiujie.service.registration.RegistrationService;
import com.qiujie.util.IpUtil;
import com.qiujie.vo.registration.RegistrationDetailVO;
import com.qiujie.vo.registration.RegistrationSubmitVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工自助注册接口（B3，registration-design §3.1）。
 * <p>
 * 公开面<b>仅 R-2 提交</b>（{@code POST /api/v1/registration}，已在 {@code PublicEndpoints} 白名单）；
 * R-3 详情 ADMIN-only（不在白名单，拦截器 fail-closed）。
 * 路由顺序：静态段 {@code /registration} 与变量段 {@code /registration/{applyNo}} 段数不同，不互相遮蔽。
 */
@RestController
@RequestMapping("/api/v1/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /** R-2 提交注册申请（公开；来源 IP 留痕，出参对「是否已注册」恒定） */
    @PostMapping
    public Result<RegistrationSubmitVO> submit(@Valid @RequestBody RegistrationSubmitRequest request,
                                               HttpServletRequest httpRequest) {
        return Result.ok(registrationService.submit(request, IpUtil.getClientIp(httpRequest)));
    }

    /** R-3 申请单详情（ADMIN-only；按申请编号，出参脱敏、不回传凭据） */
    @RequireRoles({"ADMIN"})
    @GetMapping("/{applyNo}")
    public Result<RegistrationDetailVO> detail(@PathVariable String applyNo) {
        return Result.ok(registrationService.detail(applyNo));
    }
}
