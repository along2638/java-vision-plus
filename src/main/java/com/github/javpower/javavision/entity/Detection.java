package com.github.javpower.javavision.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * YOLOv8 通用目标检测的单条结果。
 */
@Schema(description = "YOLOv8 通用目标检测结果")
public class Detection{

    @Schema(description = "类别标签名称，如 person、car、dog", example = "person")
    public String label;

    @Schema(description = "类别编号，对应模型 labels 列表的下标，从 0 开始", example = "0")
    private Integer clsId;

    @Schema(description = "检测框坐标，长度为 4 的数组：左上角 x、左上角 y、右下角 x、右下角 y（像素坐标）", example = "[100.0, 120.0, 260.0, 380.0]")
    public float[] bbox;

    @Schema(description = "置信度，取值 0~1，越大越可信", example = "0.92")
    public float confidence;


    public Detection(String label,Integer clsId, float[] bbox, float confidence){
        this.clsId = clsId;
        this.label = label;
        this.bbox = bbox;
        this.confidence = confidence;
    }

    public Detection(){

    }

    public Integer getClsId() {
        return clsId;
    }

    public void setClsId(Integer clsId) {
        this.clsId = clsId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public float[] getBbox() {
        return bbox;
    }

    public void setBbox(float[] bbox) {
    }


    @Override
    public String toString() {
        return "  label="+label +
                " \t clsId="+clsId +
                " \t x0="+bbox[0] +
                " \t y0="+bbox[1] +
                " \t x1="+bbox[2] +
                " \t y1="+bbox[3] +
                " \t score="+confidence;
    }
}
