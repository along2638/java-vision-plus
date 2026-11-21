package com.github.javpower.javavision.db;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人脸库人员信息，替代原先的 image/face/{personId}.json 文件。
 * <p>
 * 表结构遵循建表规约：代理主键 id、必备 create_time / update_time、
 * 0/1 标志位 is_deleted（POJO 侧字段名为 deleted，不加 is 前缀）。
 * 特征向量体积较大，按规约独立存放于 {@link FaceFeatureRecord}。
 */
@Data
@TableName("vision_face_person")
public class FaceRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 人员业务编号，由调用方传入；未删除记录内唯一
     */
    @TableField("person_id")
    private String personId;

    @TableField("person_name")
    private String personName;

    /**
     * 人脸图片地址（MinIO URL 或本地绝对路径）
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 特征维度，冗余自特征表，便于列表展示时不回查大文本
     */
    @TableField("feature_dim")
    private Integer featureDim;

    /**
     * 逻辑删除标记：0 未删除，1 已删除。
     * 查询自动追加 is_deleted = 0，delete 自动改写为 update 置 1
     */
    @JsonIgnore
    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
