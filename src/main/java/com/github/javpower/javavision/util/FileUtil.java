package com.github.javpower.javavision.util;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.javpower.javavision.config.FileProperties;
import com.github.javpower.javavision.storage.LocalFileStorage;
import com.github.javpower.javavision.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * 上传文件统一入口。
 * <p>
 * 两类用途分开：
 * <ul>
 *   <li>{@link #getTempPath}：一次性推理的临时文件，落在本地 uploadPath，用完按配置删除；</li>
 *   <li>{@link #upload}：需要长期保存的资源（人脸图、图库图），交给 {@link StorageService}
 *       （MinIO 或本地磁盘），返回可直接访问的地址。</li>
 * </ul>
 */
@Slf4j
@Service
public class FileUtil {

    private final FileProperties fileProperties;

    private final StorageService storageService;

    /**
     * 临时文件目录，启动时解析一次，避免依赖运行时工作目录
     */
    private final Path uploadDir;

    public FileUtil(FileProperties fileProperties, StorageService storageService) {
        this.fileProperties = fileProperties;
        this.storageService = storageService;
        this.uploadDir = LocalFileStorage.resolveDir(fileProperties.getUploadPath(), "image");
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
            log.info("临时文件目录：{}，存储方式：{}，处理完成后删除临时文件：{}",
                    uploadDir, storageService.isRemote() ? "MinIO" : "本地磁盘", fileProperties.isDeleteAfterUse());
        } catch (IOException e) {
            throw new IllegalStateException("创建上传目录失败：" + uploadDir, e);
        }
    }

    /**
     * 一次性推理用的临时文件（时间戳命名）。
     *
     * @return 本地绝对路径
     */
    public String getTempPath(MultipartFile file) throws IOException {
        String name = DateUtil.format(new Date(), DatePattern.PURE_DATETIME_MS_PATTERN) + RandomUtil.randomNumbers(6);
        return LocalFileStorage.save(file, uploadDir, name).toString();
    }

    /**
     * 保存需要长期保留的资源，返回可访问地址。
     *
     * @param prefix 业务前缀，如 face / image
     */
    public String upload(MultipartFile file, String prefix) throws IOException {
        return storageService.upload(file, prefix);
    }

    /**
     * 把地址物化成推理可读的本地文件。
     */
    public Path materialize(String url) throws IOException {
        return storageService.materialize(url);
    }

    /**
     * 释放 {@link #materialize} 产生的临时文件。
     */
    public void release(Path localPath) {
        storageService.release(localPath);
    }

    /**
     * 删除存储中的文件。
     */
    public void delete(String url) {
        storageService.delete(url);
    }

    /**
     * 按 ocr.file.delete-after-use 配置删除一次性文件，删除失败只告警不影响业务。
     */
    public void deleteIfNeeded(String path) {
        if (!fileProperties.isDeleteAfterUse() || path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("删除临时文件失败：{}", path, e);
        }
    }
}
