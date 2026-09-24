package com.qiujie.service.notification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.PageResult;
import com.qiujie.dto.notification.NotificationPublishRequest;
import com.qiujie.dto.notification.NotificationQuery;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Notification;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.NotificationMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.notification.NotificationService;
import com.qiujie.util.UserContext;
import com.qiujie.vo.notification.NotificationVO;
import com.qiujie.vo.notification.PublishResultVO;
import com.qiujie.vo.notification.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内通知服务实现（api.md §7.1 / Mock {@code notification.js}，架构 §6.2 P2）。
 * <p>
 * 越权红线：查询、详情、已读、未读统计一律以 {@link UserContext#getUserId()} 收口，
 * 只操作当前登录人的通知；详情/已读对「不存在」与「非本人」不区分（一律 9001），不泄露他人通知的存在性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 发布放行的通知类型（含 5/6 请假类型；新增值必须同步放行，见 api.md §7.3） */
    private static final Set<Integer> PUBLISH_TYPES = Set.of(1, 2, 3, 4, 5, 6);
    /** 发布范围 */
    private static final Set<String> PUBLISH_SCOPES = Set.of("ALL", "STATION", "EMPLOYEE");
    /** 标题长度区间 */
    private static final int TITLE_MIN = 1;
    private static final int TITLE_MAX = 100;
    /** 内容长度区间 */
    private static final int CONTENT_MIN = 1;
    private static final int CONTENT_MAX = 500;
    /** 默认通知类型 = 系统公告 */
    private static final int DEFAULT_TYPE = 4;

    private final NotificationMapper notificationMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationVO> page(NotificationQuery query) {
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Notification::getId, Notification::getType, Notification::getTitle, Notification::getContent,
                Notification::getBizType, Notification::getBizId, Notification::getIsRead, Notification::getReadTime,
                Notification::getIsPublished, Notification::getPublisherId, Notification::getPublisherName,
                Notification::getPublishScope, Notification::getCreateTime);
        // 数据范围：本人（不接收前端 employeeId）
        wrapper.eq(Notification::getEmployeeId, currentUserId());
        if (query.getIsRead() != null) {
            wrapper.eq(Notification::getIsRead, query.getIsRead());
        }
        wrapper.orderByDesc(Notification::getCreateTime).orderByDesc(Notification::getId);

        Page<Notification> page = notificationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountVO unreadCount() {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getEmployeeId, currentUserId())
                .eq(Notification::getIsRead, 0);
        Long count = notificationMapper.selectCount(wrapper);
        return new UnreadCountVO(count == null ? 0L : count);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationVO detail(Long id) {
        return toVO(findMine(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAll() {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getEmployeeId, currentUserId())
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadTime, now)
                // 条件更新不走实体自动填充，手动维护 update_time（决策 D8）
                .set(Notification::getUpdateTime, now);
        notificationMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO markRead(Long id) {
        Notification notification = findMine(id);
        // 幂等：已读则不改动（read_time 保留首次已读时间，与 Mock 一致）
        if (notification.getIsRead() == null || notification.getIsRead() == 0) {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Notification::getId, notification.getId())
                    .set(Notification::getIsRead, 1)
                    .set(Notification::getReadTime, now)
                    .set(Notification::getUpdateTime, now);
            notificationMapper.update(null, wrapper);
            notification.setIsRead(1);
            notification.setReadTime(now);
            notification.setUpdateTime(now);
        }
        return toVO(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishResultVO publish(NotificationPublishRequest request) {
        NotificationPublishRequest safe = request == null ? new NotificationPublishRequest() : request;
        if (!textLen(safe.getTitle(), TITLE_MIN, TITLE_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题长度须为 1-100 字符");
        }
        if (!textLen(safe.getContent(), CONTENT_MIN, CONTENT_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容长度须为 1-500 字符");
        }
        int type = safe.getType() == null ? DEFAULT_TYPE : safe.getType();
        if (!PUBLISH_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "通知类型非法");
        }
        String scope = safe.getScope() == null ? "" : safe.getScope().trim().toUpperCase();
        if (!PUBLISH_SCOPES.contains(scope)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PUBLISH_SCOPE_INVALID);
        }

        List<Long> targetIds = resolveTargets(scope, safe);
        String title = safe.getTitle().trim();
        String content = safe.getContent().trim();
        Long publisherId = currentUserId();
        String publisherName = loadRealName(publisherId);

        for (Long targetId : targetIds) {
            Notification notification = new Notification();
            notification.setEmployeeId(targetId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setBizType(null);
            notification.setBizId(null);
            notification.setIsRead(0);
            notification.setReadTime(null);
            notification.setIsPublished(1);
            notification.setPublisherId(publisherId);
            notification.setPublisherName(publisherName);
            notification.setPublishScope(scope);
            // TODO(扩展): 收件人规模增大（跨多驿站全员）时改为批量插入（saveBatch / 多值 INSERT），降低扇出 N 次 DB 往返。
            notificationMapper.insert(notification);
        }
        return new PublishResultVO(targetIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendSystem(Long employeeId, int type, String title, String content, String bizType, Long bizId) {
        // 接收人缺失：直接不落库（调用方已留 NOTIFY_SKIP 排障痕迹，见请假域 notify）
        if (employeeId == null) {
            return;
        }
        if (!PUBLISH_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "通知类型非法");
        }
        if (!textLen(title, TITLE_MIN, TITLE_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题长度须为 1-100 字符");
        }
        if (!textLen(content, CONTENT_MIN, CONTENT_MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容长度须为 1-500 字符");
        }
        Notification notification = new Notification();
        notification.setEmployeeId(employeeId);
        notification.setType(type);
        notification.setTitle(title.trim());
        notification.setContent(content.trim());
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setIsRead(0);
        notification.setReadTime(null);
        // 系统联动：is_published=0，无发布人与范围（前端按此与公告区分）
        notification.setIsPublished(0);
        notification.setPublisherId(null);
        notification.setPublisherName(null);
        notification.setPublishScope(null);
        notificationMapper.insert(notification);
    }

    // ==================== 私有方法 ====================

    /**
     * 解析发布范围对应的收件人 id 列表（一律取「在职且启用」员工）。
     * 范围参数不合法（STATION 缺驿站/驿站不存在、EMPLOYEE 目标为空）统一 9002，与 Mock 逐条一致。
     */
    private List<Long> resolveTargets(String scope, NotificationPublishRequest request) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId).eq(Employee::getStatus, 1);
        if ("STATION".equals(scope)) {
            Long stationId = request.getStationId();
            if (stationId == null || stationMapper.selectById(stationId) == null) {
                throw new BusinessException(ErrorCode.NOTIFICATION_PUBLISH_SCOPE_INVALID);
            }
            wrapper.eq(Employee::getStationId, stationId);
            return selectEmployeeIds(wrapper);
        }
        if ("EMPLOYEE".equals(scope)) {
            List<Long> employeeIds = request.getEmployeeIds();
            if (employeeIds == null || employeeIds.isEmpty()) {
                throw new BusinessException(ErrorCode.NOTIFICATION_PUBLISH_SCOPE_INVALID);
            }
            // 去重后查询：ids 里可能含重复/无效 id，最终以命中的在职启用员工为准
            Set<Long> ids = new LinkedHashSet<>(employeeIds);
            ids.removeIf(java.util.Objects::isNull);
            if (ids.isEmpty()) {
                throw new BusinessException(ErrorCode.NOTIFICATION_PUBLISH_SCOPE_INVALID);
            }
            wrapper.in(Employee::getId, ids);
            List<Long> targets = selectEmployeeIds(wrapper);
            if (targets.isEmpty()) {
                throw new BusinessException(ErrorCode.NOTIFICATION_PUBLISH_SCOPE_INVALID);
            }
            return targets;
        }
        // ALL：全部在职且启用
        return selectEmployeeIds(wrapper);
    }

    private List<Long> selectEmployeeIds(LambdaQueryWrapper<Employee> wrapper) {
        List<Long> ids = new ArrayList<>();
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            ids.add(employee.getId());
        }
        return ids;
    }

    /** 取当前登录人的通知；不存在 / 非本人一律 9001（不区分，防他人通知存在性探测） */
    private Notification findMine(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_EXISTS);
        }
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getId, id)
                .eq(Notification::getEmployeeId, currentUserId());
        Notification notification = notificationMapper.selectOne(wrapper);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_EXISTS);
        }
        return notification;
    }

    private String loadRealName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        return employee == null ? null : employee.getRealName();
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        // 已认证端点必有登录态；防御性兜底避免空 id 造成全表条件退化
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private List<NotificationVO> toVOList(List<Notification> records) {
        List<NotificationVO> list = new ArrayList<>(records.size());
        for (Notification record : records) {
            list.add(toVO(record));
        }
        return list;
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setBizType(notification.getBizType());
        vo.setBizId(notification.getBizId());
        vo.setIsRead(notification.getIsRead() != null && notification.getIsRead() == 1);
        vo.setReadTime(notification.getReadTime());
        vo.setCreateTime(notification.getCreateTime());
        vo.setIsPublished(notification.getIsPublished() != null && notification.getIsPublished() == 1);
        vo.setPublisherId(notification.getPublisherId());
        vo.setPublisherName(notification.getPublisherName());
        vo.setPublishScope(notification.getPublishScope());
        return vo;
    }

    /** 文本长度：按 trim 后字符数判定（对齐 Mock {@code textLen}） */
    private boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }
}
