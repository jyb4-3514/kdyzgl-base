package com.qiujie.vo.leave;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请假操作留痕出参（api.md §7.4 {@code handleLog[]}）。
 * <p>
 * {@code before}/{@code after} 为变更前后快照对象（数据库存 JSON 文本，出参解析成对象），
 * 结构随动作而变（编辑留前后值，审批只留备注），故用 Object 承载。
 */
@Data
public class LeaveLogVO {

    private Long id;

    private Long leaveId;

    /** 动作：SUBMIT/UPDATE/RESUBMIT/CANCEL/STATION_APPROVE/STATION_REJECT/FINAL_APPROVE/FINAL_REJECT/REVOKE/NOTIFY_SKIP */
    private String action;

    private Long operatorId;

    private String operatorName;

    private String operatorRole;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime time;

    private String fromStatus;

    private String toStatus;

    /** 变更前快照 */
    private Object before;

    /** 变更后快照 */
    private Object after;

    /** 备注（驳回原因 / 撤回原因 / 排障说明） */
    private String remark;
}
