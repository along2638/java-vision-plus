package com.github.javpower.javavision.storage;

import com.github.javpower.javavision.config.FileProperties;
import com.github.javpower.javavision.config.MinioProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储实现装配：minio.enabled=true 用 MinIO，否则回退本地磁盘。
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public StorageService minioStorageService(MinioClient client, MinioProperties properties, FileProperties fileProperties) {
        return new MinioStorageService(client, properties, fileProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "false", matchIfMissing = true)
    public StorageService localStorageService(FileProperties fileProperties) {
        return new LocalStorageService(fileProperties);
    }
}
