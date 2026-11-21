package com.github.javpower.javavision.framework.exception;

import com.github.javpower.javavision.framework.response.ApiCode;
import com.github.javpower.javavision.framework.response.ApiResult;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

/**
 * 统一 /error 的输出格式。
 * <p>
 * 替换 Spring Boot 默认的 BasicErrorController，避免 404/405 等场景返回
 * {@code {"timestamp":...,"status":404,"error":...}} 这种与业务接口不一致的结构。
 */
@Slf4j
@RestController
@Hidden
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ApiResult handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        int status = statusCode instanceof Integer ? (Integer) statusCode : 500;
        log.warn("请求处理失败，uri：{}，status：{}，message：{}", uri, status, message);

        if (status == 404) {
            return ApiResult.fail(ApiCode.NOT_FOUND, "接口不存在：" + uri);
        }
        if (status == 405) {
            return ApiResult.fail(ApiCode.PARAM_ERROR, "请求方法不支持：" + uri);
        }
        return ApiResult.fail(ApiCode.FAIL, message == null ? ApiCode.FAIL.getMsg() : String.valueOf(message));
    }
}
