package com.github.javpower.javavision.storage;

import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地落盘的公共逻辑，供临时文件与本地存储实现复用。
 */
public final class LocalFileStorage {

    private LocalFileStorage() {
    }

    /**
     * 解析配置中的目录，空值回退到默认值，并转为绝对路径。
     */
    public static Path resolveDir(String configured, String fallback) {
        String dir = (configured == null || configured.trim().isEmpty()) ? fallback : configured;
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * 以上传文件的原后缀保存到 dir 下。
     *
     * @param baseName 不含后缀的文件名
     * @return 落盘后的绝对路径
     */
    public static Path save(MultipartFile file, Path dir, String baseName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = baseName + "." + extensionOf(file);
        Path target = dir.resolve(fileName).normalize();
        // 防止文件名中出现 ../ 造成越权写入
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("非法的文件名：" + fileName);
        }
        Files.createDirectories(dir);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /**
     * 取后缀并做合法性校验，避免拼接出奇怪的文件名。
     */
    public static String extensionOf(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName == null ? "" : FilenameUtils.getExtension(originalFileName).toLowerCase();
        if (extension.isEmpty() || !extension.matches("[a-z0-9]{1,10}")) {
            throw new IOException("无法识别的文件后缀：" + originalFileName);
        }
        return extension;
    }
}
