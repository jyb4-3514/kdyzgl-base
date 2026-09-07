package com.qiujie.exception;

import com.qiujie.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：所有业务规则冲突统一抛出，由 GlobalExceptionHandler 转为统一响应。
 * 可携带 data（如导入 5003 的行级错误明细）。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    /** 附加数据（如导入错误明细），不参与异常堆栈序列化 */
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.data = null;
    }

    /** 覆盖默认提示（如 404 的 "{资源}不存在"、400 的具体字段说明） */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.code = errorCode.getCode();
        this.data = data;
    }
}
