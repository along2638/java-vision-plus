package com.github.javpower.javavision.framework.exception;


import com.github.javpower.javavision.framework.config.ApiCompatProperties;
import com.github.javpower.javavision.framework.response.ApiCode;
import com.github.javpower.javavision.framework.response.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.List;

/**
 * 全局异常处理。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li>HTTP 状态码按语义返回，不再一律 200，调用方与网关能直接按状态码做重试/告警；</li>
 *   <li>响应体只给用户可读的提示，堆栈、SQL、类名、约束名一律只进日志；</li>
 *   <li>每个错误响应都带 traceId（由 {@code TraceIdFilter} 写入 MDC），便于回查日志。</li>
 * </ol>
 *
 * <p>过渡期兼容：把 {@code vision.api.legacy-http-status} 设为 true，状态码退回改造前的
 * 恒 200（错误只体现在响应体 {@code code} 里），给前端留改造时间。见 {@link ApiCompatProperties}。
 *
 * @author gc.x
 * @date 2021-4-13
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 兼容开关。构造器注入，避免字段注入。
     */
    private final ApiCompatProperties compatProperties;

    public GlobalExceptionHandler(ApiCompatProperties compatProperties) {
        this.compatProperties = compatProperties;
    }

    /**
     * 业务异常：可预期的失败（用户已存在、未检测到人脸等），消息可直接展示给调用方。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResult handleBusiness(BusinessException e, HttpServletResponse response) {
        applyStatus(response, HttpStatus.BAD_REQUEST);
        log.warn("业务异常：{}", e.getMessage());
        return ApiResult.fail(e.getMessage());
    }

    /**
     * 请求体校验失败（{@code @RequestBody} + {@code @Valid}）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult handleInvalidBody(MethodArgumentNotValidException e, HttpServletResponse response) {
        String message = firstFieldError(e.getBindingResult());
        applyStatus(response, HttpStatus.BAD_REQUEST);
        log.warn("参数校验失败：{}", message);
        return ApiResult.fail(ApiCode.PARAM_ERROR, message);
    }

    /**
     * 表单/查询参数绑定失败。
     */
    @ExceptionHandler(BindException.class)
    public ApiResult handleBind(BindException e, HttpServletResponse response) {
        String message = firstFieldError(e.getBindingResult());
        applyStatus(response, HttpStatus.BAD_REQUEST);
        log.warn("参数绑定失败：{}", message);
        return ApiResult.fail(ApiCode.PARAM_ERROR, message);
    }

    /**
     * 方法级参数校验失败（{@code @Validated} + {@code @NotBlank} 等直接标在入参上）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult handleConstraintViolation(ConstraintViolationException e, HttpServletResponse response) {
        String message = "参数不合法";
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            message = violation.getMessage();
            break;
        }
        applyStatus(response, HttpStatus.BAD_REQUEST);
        log.warn("参数校验失败：{}", message);
        return ApiResult.fail(ApiCode.PARAM_ERROR, message);
    }

    /**
     * 请求格式类错误：报文无法解析、类型不匹配、缺少必填参数/文件等。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MaxUploadSizeExceededException.class})
    public ApiResult handleBadRequest(Exception e, HttpServletResponse response) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message;
        if (e instanceof HttpMessageNotReadableException) {
            message = "请求参数解析异常";
        } else if (e instanceof MethodArgumentTypeMismatchException) {
            message = "请求参数数据类型错误";
        } else if (e instanceof MissingServletRequestParameterException) {
            message = "缺少必填参数：" + ((MissingServletRequestParameterException) e).getParameterName();
        } else if (e instanceof MissingServletRequestPartException) {
            message = "缺少必填文件：" + ((MissingServletRequestPartException) e).getRequestPartName();
        } else {
            status = HttpStatus.PAYLOAD_TOO_LARGE;
            message = "上传文件超过大小限制";
        }
        applyStatus(response, status);
        log.warn("请求不合法：{}", message);
        return ApiResult.fail(ApiCode.PARAM_ERROR, message);
    }

    /**
     * 请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                              HttpServletResponse response) {
        applyStatus(response, HttpStatus.METHOD_NOT_ALLOWED);
        log.warn("请求方法不支持：{}", e.getMethod());
        return ApiResult.fail(ApiCode.PARAM_ERROR, "请求方法不支持：" + e.getMethod());
    }

    /**
     * 唯一约束冲突。
     *
     * <p>必须用 Spring 的 {@link DuplicateKeyException}（{@code org.springframework.dao}），
     * 不能用名称相同的 snakeyaml 异常——后者永远不会被抛出。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult handleDuplicateKey(DuplicateKeyException e, HttpServletResponse response) {
        applyStatus(response, HttpStatus.CONFLICT);
        log.warn("唯一约束冲突", e);
        return ApiResult.fail("数据已存在，请勿重复提交");
    }

    /**
     * 其他数据完整性问题（外键、非空等）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResult handleDataIntegrity(DataIntegrityViolationException e, HttpServletResponse response) {
        applyStatus(response, HttpStatus.CONFLICT);
        log.error("数据完整性校验失败", e);
        return ApiResult.fail("数据状态冲突，请刷新后重试");
    }

    /**
     * 兜底：任何未预期异常。
     *
     * <p>不要把 {@code exception.getMessage()} 返回给调用方：PG 的报错里带表名、列名甚至 SQL 片段，
     * 属于内部实现泄露。技术原因只写日志。
     */
    @ExceptionHandler(Exception.class)
    public ApiResult handleUnexpected(Exception e, HttpServletResponse response) {
        applyStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
        log.error("未处理异常", e);
        return ApiResult.fail("系统繁忙，请稍后重试");
    }

    /**
     * 写入 HTTP 状态码的唯一出口。
     *
     * <p>兼容模式开启时统一写 200，与改造前行为一致；这是所有异常处理器唯一需要判断开关的地方，
     * 新增处理器时只需调用本方法，不要自己写 {@code response.setStatus}。
     */
    private void applyStatus(HttpServletResponse response, HttpStatus status) {
        if (compatProperties.isLegacyHttpStatus()) {
            response.setStatus(HttpStatus.OK.value());
            return;
        }
        response.setStatus(status.value());
    }

    /**
     * 取第一条字段校验错误的消息；无错误时给通用提示，避免 NPE。
     */
    private String firstFieldError(BindingResult bindingResult) {
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return ApiCode.PARAM_ERROR.getMsg();
        }
        return fieldErrors.get(0).getDefaultMessage();
    }
}
