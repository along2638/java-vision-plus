package com.github.javpower.javavision.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 人脸业务参数。既用于新增/更新的入参，也用于人脸库列表的返回对象。
 */
@Data
@Schema(description = "人脸信息")
public class FaceParam {

    @Schema(description = "人员编号，业务唯一标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "0000")
    private String personId = "0000";

    @Schema(description = "人员姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String personName;

    @Schema(description = "人脸特征向量（虹软算法提取，256 维浮点数组）。列表接口不返回该字段，null 表示未携带")
    private List<Float> feature;

    /**
     * 人脸图片地址（MinIO URL 或本地绝对路径），便于回溯与展示
     */
    @Schema(description = "人脸图片地址（MinIO URL 或本地绝对路径），用于回溯与展示", example = "http://127.0.0.1:9000/javavision/face/xxx.jpg")
    private String imageUrl;

}
