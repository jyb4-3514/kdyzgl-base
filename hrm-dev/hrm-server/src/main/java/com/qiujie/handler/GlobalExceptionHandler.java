package com.qiujie.handler;

import com.qiujie.common.Result;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理（见 api.md 1.3）：
 * - 业务错误（含参数校验失败）HTTP 200 + 业务码；
 * - 401/403/404 同步 HTTP 状态码；
 * - 500 只记日志，对外通用提示，不暴露堆栈与内部实现。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：401/403/404 同步 HTTP 状态码，其余 HTTP 200 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus httpStatus = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(httpStatus)
                .body(Result.error(e.getCode(), e.getMessage(), e.getData()));
    }

    /** @Valid 请求体校验失败：拼接具体字段错误说明（HTTP 200 + code 400） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), joinFieldErrors(e));
    }

    /** 表单/参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("；"));
        return Result.error(ErrorCode.BAD_REQUEST.getCode(),
                message.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : message);
    }

    /** @Validated 单参数校验失败（如路径/查询参数约束） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("；"));
        return Result.error(ErrorCode.BAD_REQUEST.getCode(),
                message.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : message);
    }

    /** 请求体缺失 / JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "请求体格式不正确或缺失");
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    /** 缺少 multipart 部分（如导入未携带 file） */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingPart(MissingServletRequestPartException e) {
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数：" + e.getRequestPartName());
    }

    /** 参数类型不匹配（如路径 id 非数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "参数格式不正确：" + e.getName());
    }

    /** 上传文件超过 multipart 限制（导入 ≤ 10MB） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "上传文件过大（限制 10MB）");
    }

    /** HTTP 方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), "请求方法不被支持：" + e.getMethod());
    }

    /** 路径无对应处理器（HTTP 404 同步状态码） */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandlerFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ErrorCode.NOT_FOUND.getCode(), "接口不存在：" + e.getRequestURL()));
    }

    /** 兜底：记录完整堆栈，对外仅返回通用提示（api.md 1.3：HTTP 200 + code 500） */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }

    /** 拼接字段校验错误信息（DTO 注解 message 已为中文自描述文案） */
    private String joinFieldErrors(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("；"));
        return message.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : message;
    }
}
