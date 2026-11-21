package com.benjaminwan.ocrlibrary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * RapidOCR 识别出的文本块。
 */
@Schema(description = "OCR 文本块（RapidOCR）")
public class TextBlock {
    @Schema(description = "文本框顶点坐标，4 个点按顺时针排列，每个点包含 x、y 像素坐标")
    private final ArrayList<Point> boxPoint;

    @Schema(description = "文本框检测置信度，取值 0~1")
    private final float boxScore;

    @Schema(description = "文本方向类别：0 表示 0°、1 表示 90°、2 表示 180°、3 表示 270°")
    private final int angleIndex;

    @Schema(description = "文本方向分类置信度，取值 0~1")
    private final float angleScore;

    @Schema(description = "文本方向分类耗时，单位毫秒")
    private final double angleTime;

    @Schema(description = "识别出的文字内容", example = "安全生产人人有责")
    private final String text;

    @Schema(description = "每个字符的置信度数组，与 text 的字符一一对应，取值 0~1")
    private final float[] charScores;

    @Schema(description = "文字识别（CRNN）耗时，单位毫秒")
    private final double crnnTime;

    @Schema(description = "该文本块的总处理耗时，单位毫秒")
    private final double blockTime;

    public TextBlock(
            ArrayList<Point> boxPoint, float boxScore,
            int angleIndex, float angleScore, double angleTime,
            String text, float[] charScores, double crnnTime, double blockTime
    ) {
        this.boxPoint = boxPoint;
        this.boxScore = boxScore;
        this.angleIndex = angleIndex;
        this.angleScore = angleScore;
        this.angleTime = angleTime;
        this.text = text;
        this.charScores = charScores;
        this.crnnTime = crnnTime;
        this.blockTime = blockTime;
    }

    public ArrayList<Point> getBoxPoint() {
        return boxPoint;
    }

    public float getBoxScore() {
        return boxScore;
    }

    public int getAngleIndex() {
        return angleIndex;
    }

    public float getAngleScore() {
        return angleScore;
    }

    public double getAngleTime() {
        return angleTime;
    }

    public String getText() {
        return text;
    }

    public float[] getCharScores() {
        return charScores;
    }

    public double getCrnnTime() {
        return crnnTime;
    }

    public double getBlockTime() {
        return blockTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextBlock textBlock = (TextBlock) o;
        return Float.compare(textBlock.boxScore, boxScore) == 0 &&
                angleIndex == textBlock.angleIndex &&
                Float.compare(textBlock.angleScore, angleScore) == 0 &&
                Double.compare(textBlock.angleTime, angleTime) == 0 &&
                Double.compare(textBlock.crnnTime, crnnTime) == 0 &&
                Double.compare(textBlock.blockTime, blockTime) == 0 &&
                Objects.equals(boxPoint, textBlock.boxPoint) &&
                Objects.equals(text, textBlock.text) &&
                Arrays.equals(charScores, textBlock.charScores);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(boxPoint, boxScore, angleIndex, angleScore, angleTime, text, crnnTime, blockTime);
        result = 31 * result + Arrays.hashCode(charScores);
        return result;
    }

    @Override
    public String toString() {
        return "TextBlock{" +
                "boxPoint=" + boxPoint +
                ", boxScore=" + boxScore +
                ", angleIndex=" + angleIndex +
                ", angleScore=" + angleScore +
                ", angleTime=" + angleTime +
                ", text='" + text + '\'' +
                ", charScores=" + Arrays.toString(charScores) +
                ", crnnTime=" + crnnTime +
                ", blockTime=" + blockTime +
                '}';
    }
}
