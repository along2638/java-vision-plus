package com.github.javpower.javavision.storage;

import cn.hutool.core.lang.UUID;
import com.github.javpower.javavision.config.FileProperties;
import com.github.javpower.javavision.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * MinIO 对象存储实现。
 * <p>
 * 对象键形如 {@code face/2026/11/12/xxxx.jpg}；上传后返回的 URL 由 public-url + bucket + objectKey 拼成，
 * 桶默认设置为匿名只读，前端可直接用该地址展示图片。
 * <p>
 * 推理代码只接受本地文件，因此 {@link #materialize} 会把对象下载到 uploadPath/minio-temp 下，
 * 用完由 {@link #release} 删除。
 */
@Slf4j
public class MinioStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient client;

    private final MinioProperties properties;

    private final Path cacheDir;

    public MinioStorageService(MinioClient client, MinioProperties properties, FileProperties fileProperties) {
        this.client = client;
        this.properties = properties;
        Path uploadDir = LocalFileStorage.resolveDir(fileProperties.getUploadPath(), "image");
        this.cacheDir = uploadDir.resolve("minio-temp").normalize();
    }

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(cacheDir);
        String bucket = properties.getBucket();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            if (!properties.isAutoCreateBucket()) {
                throw new IllegalStateException("MinIO 存储桶不存在且未开启自动创建：" + bucket);
            }
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO 存储桶已创建：{}", bucket);
        }
        if (properties.isPublicRead()) {
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
        }
        log.info("MinIO 已启用：endpoint={}，bucket={}，publicUrl={}", properties.getEndpoint(), bucket, properties.getPublicUrl());
    }

    @Override
    public String upload(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String extension = LocalFileStorage.extensionOf(file);
        String objectKey = prefix + "/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.fastUUID().toString(true) + "." + extension;
        long size = file.getSize();
        // 大小未知时由 SDK 按分片上传，partSize 需显式指定
        long partSize = size > 0 ? -1 : 10L * 1024 * 1024;
        try (InputStream in = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(in, size, partSize)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new IOException("上传到 MinIO 失败：" + objectKey, e);
        }
        String url = objectUrl(objectKey);
        log.debug("文件已上传到 MinIO：{}", url);
        return url;
    }

    @Override
    public Path materialize(String url) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("文件地址不能为空");
        }
        // 兼容改造前存下来的本地绝对路径
        if (!url.startsWith("http")) {
            return Paths.get(url);
        }
        String objectKey = parseObjectKey(url);
        if (objectKey == null) {
            throw new IllegalArgumentException("无法识别该文件地址，非当前 MinIO 桶：" + url);
        }
        String extension = objectKey.contains(".") ? objectKey.substring(objectKey.lastIndexOf('.')) : "";
        Path target = cacheDir.resolve(UUID.fastUUID().toString(true) + extension).normalize();
        try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectKey)
                .build())) {
            Files.copy(response, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IOException("从 MinIO 下载失败：" + objectKey, e);
        }
        return target;
    }

    @Override
    public void release(Path localPath) {
        if (localPath == null || !localPath.normalize().startsWith(cacheDir)) {
            return;
        }
        try {
            Files.deleteIfExists(localPath);
        } catch (IOException e) {
            log.warn("清理 MinIO 临时文件失败：{}", localPath, e);
        }
    }

    @Override
    public void delete(String url) {
        String objectKey = parseObjectKey(url);
        if (objectKey == null) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("删除 MinIO 对象失败：{}", objectKey, e);
        }
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    private String objectUrl(String objectKey) {
        return properties.getPublicUrl() + "/" + properties.getBucket() + "/" + objectKey;
    }

    /**
     * 从 URL 中反解对象键，支持 public-url 与 endpoint 两种前缀。
     */
    private String parseObjectKey(String url) {
        if (url == null) {
            return null;
        }
        String[] prefixes = {
                properties.getPublicUrl() + "/" + properties.getBucket() + "/",
                properties.getEndpoint() + "/" + properties.getBucket() + "/"
        };
        for (String prefix : prefixes) {
            if (url.startsWith(prefix)) {
                return url.substring(prefix.length());
            }
        }
        return null;
    }

    private String publicReadPolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }
}
