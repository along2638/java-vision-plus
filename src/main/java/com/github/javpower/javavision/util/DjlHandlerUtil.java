package com.github.javpower.javavision.util;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Paths;

public class DjlHandlerUtil {

    /**
     * 转换模型文件路径为 URL 格式，供 DJL 的 Criteria.optModelUrls 使用。
     * <p>
     * 使用 {@link Paths#toUri()} 而不是手工拼接 "file:///"，由 JDK 负责处理平台差异：
     * Windows 下 {@code C:\a\b.zip} → {@code file:///C:/a/b.zip}；
     * Linux 下 {@code /tmp/a/b.zip} → {@code file:///tmp/a/b.zip}。
     * 手工拼接在 Linux 上会得到 {@code file:////tmp/a/b.zip}（多一个斜杠）。
     *
     * @param modelPath 原始模型文件路径，例如 "C:/Users/xxx/AppData/Local/Temp/ocrJava/onnx/image_feature.zip"（Windows）或
     *   "/tmp/ocrJava/onnx/image_feature.zip"（Linux）
     * @return 转换后的 URL 格式路径，例如 "file:///C:/Users/xxx/AppData/Local/Temp/ocrJava/onnx/image_feature.zip"（Windows）或
     *   "file:///tmp/ocrJava/onnx/image_feature.zip"（Linux）
     */
    public static String getFixedModelPath(String modelPath) {
        // 检查路径是否为空或为空白
        if (StringUtils.isBlank(modelPath)) {
            return "";
        }
        // 已经是 URL（http/https/file/s3 等）则原样返回
        if (modelPath.startsWith("http") || modelPath.startsWith("file:") || modelPath.startsWith("s3:")) {
            return modelPath;
        }
        // 由 JDK 生成规范的 file URI，跨平台一致
        return Paths.get(modelPath).toUri().toString();
    }
}
