package com.github.javpower.javavision.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * OCR / 目标检测的补充参数。
 */
@Data
@Schema(description = "OCR/检测补充参数")
public class OcrParam {

    @Schema(description = "保留参数，当前版本未使用，可不传", example = "")
    private String key;

    @Schema(description = "保留参数，当前版本未使用，可不传")
    private List<String> content;
}
