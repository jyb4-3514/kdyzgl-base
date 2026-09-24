package com.qiujie.service.support;

import com.qiujie.common.LoginUser;
import com.qiujie.enums.DataScopePolicy;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.util.UserContext;
import org.springframework.stereotype.Component;

/**
 * 资源归属校验服务（L3，见架构 1.4.2 / ADR-02）。
 * <p>
 * 跨域公共支撑：路径资源（包裹/工单/同步任务/KPI 评分/人事档案…）的「是否属于当前用户可见范围」判定只在这里实现一次，
 * 各域 Service 不再各写一份；403/404 分界由调用方按端点声明传入 {@link DataScopePolicy}，<b>不得统一</b>（ADR-07）。
 */
@Component
public class ResourceAccessChecker {

    /**
     * 校验当前登录用户是否有权访问归属 {@code resourceStationId} 的资源。
     *
     * @param resourceStationId 资源归属驿站 id；为 null 视为「无归属」（如租户不适用）
     * @param policy            越权策略：SILENT 放行；NOT_FOUND 抛 404；FORBIDDEN 抛 403
     * @throws BusinessException 401 未登录 / 404 / 403
     */
    public void check(Long resourceStationId, DataScopePolicy policy) {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // ADMIN 跨站可见；SILENT 不校验（列表类静默收敛场景）
        if (policy == DataScopePolicy.SILENT || RoleEnum.isAdmin(user.getRole())) {
            return;
        }
        if (isOwnStation(resourceStationId, user)) {
            return;
        }
        if (policy == DataScopePolicy.FORBIDDEN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // NOT_FOUND：跨站资源按「不存在」返回，避免暴露他人资源的存在性
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    /** 资源是否在当前用户可见范围内（供 Service 组装列表时直接过滤，不抛错） */
    public boolean isVisible(Long resourceStationId) {
        LoginUser user = UserContext.get();
        if (user == null) {
            return false;
        }
        return RoleEnum.isAdmin(user.getRole()) || isOwnStation(resourceStationId, user);
    }

    private boolean isOwnStation(Long resourceStationId, LoginUser user) {
        Long ownStationId = parseStationId(user.getStationId());
        return ownStationId != null && ownStationId.equals(resourceStationId);
    }

    /** 会话中的 stationId 为 String（避免 Redis JSON 序号兼容问题），此处统一转 Long */
    private Long parseStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
