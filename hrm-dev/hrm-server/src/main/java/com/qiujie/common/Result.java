package com.qiujie.common;

import com.qiujie.enums.ErrorCode;
import lombok.Data;

/**
 * 统一响应结构：{ code, message, data }（见 api.md 1.2）。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.message = ErrorCode.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> error(int code, String message) {
        return error(code, message, null);
    }

    public static <T> Result<T> error(int code, String message, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }
}
