package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.OperationAuditLog;

/**
 * 操作审计留痕 Mapper（db.md / DDL: V23）。
 * <p>
 * 追加型：业务只经 {@code insert}；应用层不使用 UPDATE / DELETE（对齐 {@code payroll_log}）。
 */
public interface OperationAuditLogMapper extends BaseMapper<OperationAuditLog> {
}
