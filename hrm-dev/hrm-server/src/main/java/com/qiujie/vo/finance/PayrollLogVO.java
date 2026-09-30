package com.qiujie.vo.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工资单操作留痕出参（api.md §4.12.14，I-7）。
 * <p>
 * {@code before}/{@code after} 为变更前后快照对象（数据库存 JSON 文本，出参解析成对象），结构随动作而变，故用 Object 承载。
 * <p>
 * <b>字段裁剪由服务端强制</b>：非 ADMIN 出参<b>不含</b> {@code before}/{@code after}/{@code operatorId}/{@code operatorRole}
 * （前端隐藏而后端照返 = 越权信息泄漏，U-14）；裁剪口径见 {@code PayrollServiceImpl#toLogVO}。
 */
@Data
public class PayrollLogVO {

    private Long id;

    /** 动作：GENERATE_AUTO/GENERATE_MANUAL/ITEM_ADD/ITEM_UPDATE/SUBMIT/APPROVE/REJECT/PUBLISH/REPUBLISH/CONFIRM/OBJECTION/PAY/NOTIFY/NOTIFY_SKIP */
    private String action;

    /** 操作人（非 ADMIN 不返回） */
    private Long operatorId;

    /** 操作人姓名（非 ADMIN 不返回） */
    private String operatorName;

    /** 操作人角色（非 ADMIN 不返回） */
    private String operatorRole;

    /** 操作主体：USER=人工，SYSTEM=自动调度（非 ADMIN 不返回） */
    private String operatorType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime time;

    /** 变更前状态（非 ADMIN 不返回） */
    private String fromStatus;

    /** 变更后状态（非 ADMIN 仍返回） */
    private String toStatus;

    /** 事由：手工加扣款事由 / 异议原因 / 驳回意见 / 再发布处理说明 */
    private String reason;

    /** 变更前快照（非 ADMIN 不返回） */
    private Object before;

    /** 变更后快照（非 ADMIN 不返回） */
    private Object after;

    /** 备注 / 排障说明（非 ADMIN 不返回） */
    private String remark;
}
