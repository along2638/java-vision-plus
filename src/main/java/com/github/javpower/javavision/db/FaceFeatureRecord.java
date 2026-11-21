package com.github.javpower.javavision.db;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人脸特征向量，1:1 对应 {@link FaceRecord}。
 * <p>
 * 按建表规约第 8 条，超过 varchar 上限的大文本独立成表，
 * 用主键（face_id）与主表对应，避免影响主表索引效率。
 * 该表为派生数据，随人员删除而物理删除，不做逻辑删除。
 * <p>
 * autoResultMap = true 必须开启，否则查询时 @TableField 上的 typeHandler 不生效，
 * feature 会读成 null。
 */
@Data
@TableName(value = "vision_face_feature", autoResultMap = true)
public class FaceFeatureRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("face_id")
    private Long faceId;

    /**
     * 特征向量，JSON 数组文本，读写由 FloatListTypeHandler 转换
     */
    @TableField(value = "feature", typeHandler = FloatListTypeHandler.class)
    private List<Float> feature;

    @TableField("feature_dim")
    private Integer featureDim;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
