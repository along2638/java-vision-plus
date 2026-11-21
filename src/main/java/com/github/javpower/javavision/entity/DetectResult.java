package com.github.javpower.javavision.entity;

import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标检测结果，坐标为相对图片的归一化值（0~1）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "目标检测结果（火焰/反光衣/安全帽）")
public class DetectResult {

    /**
     * 类别名称
     */
    @Schema(description = "检测类别名称，如 fire、smoke、vest、helmet、person", example = "helmet")
    private String className;

    /**
     * 置信度
     */
    @Schema(description = "置信度，取值 0~1，越大越可信", example = "0.87")
    private double probability;

    /**
     * 左上角 x
     */
    @Schema(description = "检测框左上角横坐标，按图片宽度归一化，取值 0~1", example = "0.12")
    private double x;

    /**
     * 左上角 y
     */
    @Schema(description = "检测框左上角纵坐标，按图片高度归一化，取值 0~1", example = "0.23")
    private double y;

    /**
     * 宽
     */
    @Schema(description = "检测框宽度，按图片宽度归一化，取值 0~1", example = "0.31")
    private double width;

    /**
     * 高
     */
    @Schema(description = "检测框高度，按图片高度归一化，取值 0~1", example = "0.45")
    private double height;

    /**
     * 转换 DJL 检测结果，避免直接输出 corners 等冗余结构
     */
    public static List<DetectResult> from(DetectedObjects detections) {
        List<DetectResult> results = new ArrayList<>();
        if (detections == null) {
            return results;
        }
        // items() 是泛型方法 <T extends Classifications.Classification> List<T>，
        // 在 for-each 中没有目标类型可推断，会退化成 Classification，因此先赋给带类型的变量
        List<DetectedObjects.DetectedObject> items = detections.items();
        for (DetectedObjects.DetectedObject item : items) {
            BoundingBox box = item.getBoundingBox();
            Rectangle bounds = box.getBounds();
            results.add(new DetectResult(item.getClassName(), item.getProbability(),
                    bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        }
        return results;
    }
}
