package com.github.javpower.javavision.framework.filter;

import com.github.javpower.javavision.framework.constant.CommonConstant;
import org.slf4j.MDC;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器：为每个请求生成（或沿用上游透传的）traceId。
 *
 * <p>traceId 同时写入三处：
 * <ul>
 *   <li>MDC，日志 pattern 通过 {@code %X{traceId}} 输出，一次请求的全部日志可串起来；</li>
 *   <li>响应头 {@code X-Trace-Id}，调用方拿到后反馈给后端即可定位；</li>
 *   <li>统一响应体 {@code ApiResult.traceId}，前端报错时可直接带回来。</li>
 * </ul>
 *
 * <p>必须在 {@code finally} 中清理 MDC：Tomcat 线程是复用的，不清理会把上一个请求的
 * traceId 带到下一个请求里，日志会串味。
 *
 * @author gc.x
 * @date 2026-11-16
 */
public class TraceIdFilter implements Filter {

    /**
     * 透传 traceId 的最大长度，超长视为非法并重新生成，避免日志被异常输入撑爆
     */
    private static final int MAX_TRACE_LENGTH = 64;

    @Override
    public void init(FilterConfig filterConfig) {
        // 无初始化参数
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = resolveTraceId(request);
        MDC.put(CommonConstant.TRACE_ID, traceId);
        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader(CommonConstant.TRACE_HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CommonConstant.TRACE_ID);
        }
    }

    @Override
    public void destroy() {
        // 无资源需要释放
    }

    /**
     * 优先沿用上游传入的 traceId，缺失或非法时生成新的。
     */
    private String resolveTraceId(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            String fromHeader = ((HttpServletRequest) request).getHeader(CommonConstant.TRACE_HEADER);
            if (isUsable(fromHeader)) {
                return fromHeader.trim();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 只接受长度受限、不含空白与控制字符的 traceId，其余一律重新生成。
     */
    private boolean isUsable(String candidate) {
        if (candidate == null) {
            return false;
        }
        String value = candidate.trim();
        if (value.isEmpty() || value.length() > MAX_TRACE_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }
}
