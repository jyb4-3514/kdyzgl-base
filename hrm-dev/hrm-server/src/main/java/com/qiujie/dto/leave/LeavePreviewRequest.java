package com.qiujie.dto.leave;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请假试算入参（api.md §7.1 #2）。只算不落库，{@code reason} 可省（缺省补「试算」）。
 * <p>
 * 字段与 {@link LeaveApplyRequest} 完全一致（同一份表单的试算形态），故继承复用，
 * 避免两处重复声明后字段漂移；{@code reason} 在试算路径不参与校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeavePreviewRequest extends LeaveApplyRequest {
}
