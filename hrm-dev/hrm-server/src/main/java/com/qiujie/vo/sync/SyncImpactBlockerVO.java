package com.qiujie.vo.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影响面-阻断原因（错误码 + 文案，前端据此决定是否可删）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncImpactBlockerVO {

    private int code;
    private String message;
}
