package com.github.javpower.javavision.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 人脸检索命中结果。
 */
@Data
@Schema(description = "人脸检索命中的人员信息")
public class PersonObject {

    @Schema(description = "人员编号，命中的业务唯一标识", example = "0000")
    private String personId;

    @Schema(description = "人员姓名", example = "张三")
    private String personName;
}
