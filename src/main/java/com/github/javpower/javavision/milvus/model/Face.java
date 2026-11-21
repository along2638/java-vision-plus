package com.github.javpower.javavision.milvus.model;

import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.milvus.plus.annotation.*;

import java.util.List;

@Data
@MilvusCollection(name = "face_collection")
@GenerateMilvusMapper
@Schema(description = "虹软人脸记录（Milvus 集合 face_collection 的一行）")
public class Face {
    @MilvusField(
            name = "person_id", // 字段名称
            dataType = DataType.VarChar, // 数据类型为64位整数
            isPrimaryKey = true, // 标记为主键
            autoID = false // 假设这个ID是自动生成的
    )
    @Schema(description = "人员编号，集合主键", example = "0000")
    private String personId; // 人员的唯一标识符

    @MilvusField(
            name = "image_url",
            dataType = DataType.VarChar
    )
    @Schema(description = "人脸图片访问地址")
    private String url;

    @MilvusField(
            name = "person_name",
            dataType = DataType.VarChar
    )
    @Schema(description = "人员姓名", example = "张三")
    private String personName; // 人员姓名


    @MilvusField(
            name = "face_vector", // 字段名称
            dataType = DataType.FloatVector, // 数据类型为浮点型向量
            dimension = 256 // 向量维度，假设人脸特征向量的维度是128
    )
    @MilvusIndex(
            indexType = IndexParam.IndexType.IVF_FLAT, // 使用IVF_FLAT索引类型
            metricType = IndexParam.MetricType.IP, // 使用L2距离度量类型
            indexName = "face_index", // 索引名称
            extraParams = { // 指定额外的索引参数
                    @ExtraParam(key = "nlist", value = "16384") // 例如，IVF的nlist参数
            }
    )
    @Schema(description = "人脸特征向量，256 维浮点数组，使用内积（IP）度量相似度")
    private List<Float> faceVector; // 存储人脸特征的向量
}
