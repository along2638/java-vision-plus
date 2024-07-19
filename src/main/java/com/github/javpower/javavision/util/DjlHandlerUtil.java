package com.github.javpower.javavision.util;

import org.apache.commons.lang3.StringUtils;

public class DjlHandlerUtil {

    /**
    * 转换模型文件路径为URL格式。
    * <p>
    * 如果提供的路径是空白的，将返回空字符串。如果路径以 "http" 开头，表示它已经是一个有效的URL，
    * 因此直接返回。否则，将转换Windows风格的文件路径为URL格式的字符串。
    * @param modelPath 原始模型文件路径，例如 "C:/Users/wosui/AppData/Local/Temp/ocrJava/onnx/image_feature.zip"（Windows）或
    *   "/home/wosui/Temp/ocrJava/onnx/image_feature.zip"（Linux）
    * @return 转换后的URL格式路径，例如 "file:///C:/Users/wosui/AppData/Local/Temp/ocrJava/onnx/image_feature.zip"（Windows）或 
    *   "file:///home/wosui/Temp/ocrJava/onnx/image_feature.zip"（Linux）
    */
    public static String getFixedModelPath(String modelPath) {
        // 检查路径是否为空或为空白
        if (StringUtils.isBlank(modelPath)) {
            return "";
        }
        // 如果路径已经是HTTP URL，则直接返回
        if (modelPath.startsWith("http")) {
            return modelPath;
        }
        // 将路径中的反斜杠替换为正斜杠，以符合URL格式
        String fixedPath = modelPath.replace("\\", "/");
        // 构建URL格式的文件路径
        return "file://" + fixedPath;
    }
}
