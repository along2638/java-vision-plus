package com.github.javpower.javavision.es.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 以图搜图的单条命中结果。
 */
@Data
@AllArgsConstructor
@Schema(description = "以图搜图命中结果")
public class SearchResult {

    @Schema(description = "命中图片的访问地址（MinIO URL 或本地绝对路径）")
    private String url;

    @Schema(description = "命中图片的业务编号", example = "img-0001")
    private String imageId;

    @Schema(description = "相似度得分，取值 0~1，越大越相似", example = "0.93")
    private Float score;
}
