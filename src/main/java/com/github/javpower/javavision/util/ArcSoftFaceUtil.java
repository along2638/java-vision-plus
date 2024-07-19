package com.github.javpower.javavision.util;

import java.util.ArrayList;
import java.util.List;

public class ArcSoftFaceUtil {
    /**
     *
     * 虹软特征字节数组去除混淆并转换为浮点型向量
     * 如果registerOrNot设置为false，则忽略前256个特征值
     * @return 转换后的浮点型向量列表
     */
    public static List<Float> convertToFloatVector(byte[] featureData, boolean registerOrNot) {
        List<Float> floatVector = new ArrayList<Float>(256);
        int featureSize = registerOrNot ? 512 : 512 - 256; // 根据registerOrNot决定特征数量
        int startIndex = 8; // 跳过前8个字节
        for (int i = startIndex; i < featureData.length - 3 && floatVector.size() < featureSize; i += 4) {
            int floatInt = 0;
            for (int byteIndex = 0; byteIndex < 4; byteIndex++) {
                floatInt |= (featureData[i + byteIndex] & 0xFF) << (byteIndex * 8);
            }
            float featureValue = Float.intBitsToFloat(floatInt);
            floatVector.add(featureValue);
        }
        return floatVector;
    }
}
