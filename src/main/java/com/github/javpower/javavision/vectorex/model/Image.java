package com.github.javpower.javavision.vectorex.model;

import io.github.javpower.vectorex.keynote.model.MetricType;
import io.github.javpower.vectorexcore.annotation.VectoRexCollection;
import io.github.javpower.vectorexcore.annotation.VectoRexField;
import io.github.javpower.vectorexcore.entity.DataType;
import lombok.Data;

import java.util.List;

@Data
@VectoRexCollection(name = "image_collection")
public class Image {

    @VectoRexField(name = "image_id",isPrimaryKey = true)
    private String imageId; // 人员的唯一标识符


    @VectoRexField(name = "image_url")
    private String url;


    @VectoRexField(name = "image_vector", dataType = DataType.FloatVector, dimension = 1024,metricType = MetricType.FLOAT_COSINE_DISTANCE)
    private List<Float> imageVector; // 存储人脸特征的向量
}