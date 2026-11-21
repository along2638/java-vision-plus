package com.github.javpower.javavision.db;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图库元数据。向量本身仍在向量库（vectorex / milvus / es），
 * PG 里保存业务属性，用于列表查询、按 MD5 去重、删除时定位原图。
 */
@Data
@TableName("vision_image")
@Schema(description = "图库元数据（vision_image 表的一行）")
public class ImageRecord {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键，数据库自增")
    private Long id;

    /**
     * 图片业务编号，由调用方传入；未删除记录内唯一
     */
    @TableField("image_id")
    @Schema(description = "图片业务编号，由调用方传入，未删除记录内唯一", example = "img-0001")
    private String imageId;

    @TableField("url")
    @Schema(description = "图片访问地址（MinIO URL 或本地绝对路径）")
    private String url;

    /**
     * 原图内容摘要，定长 32 位，用于重复图片命中缓存与去重
     */
    @TableField("md5")
    @Schema(description = "原图内容摘要，32 位小写 MD5，用于去重与向量缓存命中")
    private String md5;

    @TableField("vector_dim")
    @Schema(description = "图片向量维度", example = "512")
    private Integer vectorDim;

    /**
     * 向量库类型：vectorex / milvus / es
     */
    @TableField("index_type")
    @Schema(description = "向量库类型：vectorex / milvus / es", example = "vectorex")
    private String indexType;

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
    @Schema(description = "创建时间，格式 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "最后更新时间，格式 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
