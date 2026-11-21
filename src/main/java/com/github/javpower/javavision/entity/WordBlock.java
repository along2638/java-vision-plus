package com.github.javpower.javavision.entity;

import ai.djl.modality.cv.output.Point;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * OCR 文本块（DJL 模型输出的结构化结果）。
 */
@Data
@Schema(description = "OCR 文本块")
public class WordBlock {

    @Schema(description = "识别出的文字内容", example = "安全生产人人有责")
    private String text;

    @Schema(description = "每个字符的置信度数组，与 text 的字符一一对应，取值 0~1")
    private float[] charScores;

    @Schema(description = "文本框顶点坐标，固定 4 个点，按顺时针排列；每个点包含 x、y 像素坐标")
    private List<Point> boxPoint;
}
