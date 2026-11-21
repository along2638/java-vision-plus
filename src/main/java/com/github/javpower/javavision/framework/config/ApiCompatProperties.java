package com.github.javpower.javavision.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API 兼容性开关。
 *
 * <p>只放过渡期使用的开关，业务配置不要往这里加。每个开关都要写清楚"什么时候可以删掉"，
 * 否则兼容代码会永久留在代码库里。
 *
 * @author gc.x
 */
@Data
@ConfigurationProperties(prefix = "vision.api")
@Component
public class ApiCompatProperties {

    /**
     * 错误响应是否沿用改造前的"HTTP 状态码恒为 200"行为。
     *
     * <p>改造前所有异常都返回 200，错误信息只体现在响应体 {@code code} 字段里，
     * 部分前端代码因此写了 {@code if (res.status === 200)} 的分支。把本开关设为 true，
     * 异常处理会退回旧行为，给前端留出改造时间。
     *
     * <p>默认 false（按语义返回 400/405/409/413/500）。前端改造完成后应删除本开关。
     */
    private boolean legacyHttpStatus = false;
}
