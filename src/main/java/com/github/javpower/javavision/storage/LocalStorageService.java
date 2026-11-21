package com.github.javpower.javavision.storage;

import cn.hutool.core.lang.UUID;
import com.github.javpower.javavision.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘存储（minio.enabled=false 时使用），行为与改造前一致。
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path uploadDir;

    public LocalStorageService(FileProperties fileProperties) {
        this.uploadDir = LocalFileStorage.resolveDir(fileProperties.getUploadPath(), "image");
    }

    @Override
    public String upload(MultipartFile file, String prefix) throws IOException {
        Path dir = uploadDir.resolve(prefix).normalize();
        String baseName = prefix + "_" + UUID.fastUUID().toString(true);
        Path target = LocalFileStorage.save(file, dir, baseName);
        log.debug("文件已保存到本地：{}", target);
        return target.toString();
    }

    @Override
    public Path materialize(String url) {
        return Paths.get(url);
    }

    @Override
    public void release(Path localPath) {
        // 本地模式下文件就是业务数据，不做删除
    }

    @Override
    public void delete(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(url));
        } catch (IOException e) {
            log.warn("删除本地文件失败：{}", url, e);
        }
    }

    @Override
    public boolean isRemote() {
        return false;
    }
}
