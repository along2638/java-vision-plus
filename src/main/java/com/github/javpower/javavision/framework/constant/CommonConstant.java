package com.github.javpower.javavision.framework.constant;

/**
 * 公共常量
 *
 * @author gc.x
 * @date 2018-11-08
 */
public interface CommonConstant {

    /**
     * 请求的原始字符串
     */
    String REQUEST_PARAM_BODY_STRING = "REQUEST_PARAM_BODY_STRING";

    /**
     * 链路追踪 ID 在 MDC 中的键名，日志 pattern 通过 %X{traceId} 引用
     */
    String TRACE_ID = "traceId";

    /**
     * 上游透传 traceId 的请求头，同时用于回写响应头
     */
    String TRACE_HEADER = "X-Trace-Id";

}
