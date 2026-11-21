package com.github.javpower.javavision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置。
 *
 * @author gc.x
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * 是否启用 MinIO，false 时回退为本地磁盘存储
     */
    private boolean enabled = false;

    /**
     * 服务端地址，例如 http://127.0.0.1:9000
     */
    private String endpoint = "http://127.0.0.1:9000";

    private String accessKey;

    private String secretKey;

    /**
     * 存储桶名称，不存在时会按 autoCreateBucket 自动创建
     */
    private String bucket = "javavision";

    /**
     * 对外访问地址，容器内网/公网地址不一致时单独配置；为空则取 endpoint
     */
    private String publicUrl;

    /**
     * 是否把桶策略设置为匿名只读（前端可直接用返回的 URL 展示图片）
     */
    private boolean publicRead = true;

    /**
     * 启动时桶不存在是否自动创建
     */
    private boolean autoCreateBucket = true;

    public String getPublicUrl() {
        return publicUrl == null || publicUrl.trim().isEmpty() ? endpoint : publicUrl;
    }
}
