package com.github.javpower.javavision.util;

import org.apache.commons.lang3.StringUtils;

public class DjlHandlerUtil {

    /**
     * 获取修复后的模型路径
     * @param modelPath 如 C:\Users\wosui\AppData\Local\Temp\ocrJava\onnx\image_feature.zip
     * @return  file:///C:/Users/wosui/AppData/Local/Temp/ocrJava/onnx/image_feature.zip
     */
    public static String getFixModelPath(String modelPath){
        if(StringUtils.isBlank(modelPath)){
            return "";
        }
        StringBuffer path=new StringBuffer();
        if(!modelPath.startsWith("http")){
            modelPath=modelPath.replace("\\","/");
            path.append("file:///").append(modelPath);
        }
        return path.toString();
    }
}
