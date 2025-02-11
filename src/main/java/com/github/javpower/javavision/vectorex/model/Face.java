package com.github.javpower.javavision.vectorex.model;

import io.github.javpower.vectorex.keynote.model.MetricType;
import io.github.javpower.vectorexcore.annotation.VectoRexCollection;
import io.github.javpower.vectorexcore.annotation.VectoRexField;
import io.github.javpower.vectorexcore.entity.DataType;
import lombok.Data;

import java.util.List;

@Data
@VectoRexCollection(name = "face_collection")
public class Face {

    @VectoRexField(name = "person_id",isPrimaryKey = true)
    private String personId; // 人员的唯一标识符

    @VectoRexField(name = "image_url")
    private String url;


    @VectoRexField(name = "person_name")
    private String personName; // 人员姓名


    @VectoRexField(name = "face_vector", dataType = DataType.FloatVector, dimension = 256,metricType = MetricType.FLOAT_COSINE_DISTANCE)
    private List<Float> faceVector; // 存储人脸特征的向量
}