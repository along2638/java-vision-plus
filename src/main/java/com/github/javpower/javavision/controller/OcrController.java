package com.github.javpower.javavision.controller;


import com.benjaminwan.ocrlibrary.TextBlock;
import com.github.javpower.javavision.entity.DetectResult;
import com.github.javpower.javavision.entity.Detection;
import com.github.javpower.javavision.entity.OcrParam;
import com.github.javpower.javavision.entity.WordBlock;
import com.github.javpower.javavision.service.BizService;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.api.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author gc.x
 * @date 2023/11/6
 **/
@Slf4j
@RestController
@RequestMapping
@Tag(name = "OCR应用", description = "文字识别（RapidOcr / DJL）与目标检测（YOLOv8、火焰、反光衣、安全帽）。"
        + "所有接口均为 multipart/form-data，文件字段名固定为 file。"
        + "所有接口返回统一结构 ApiResult：{code 响应码, success 是否成功, msg 提示信息, data 业务数据, time 响应时间}")
public class OcrController {

    private final BizService bizService;

    public OcrController(BizService bizService) {
        this.bizService = bizService;
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别文字，返回拼接后的纯文本（RapidOcr）",
            description = "使用 RapidOcr 引擎识别图片中的文字，多个文本块按行拼接为一个字符串返回。"
                    + "需要坐标、置信度等结构化信息时请调用 /ocr/json。")
    @ApiResponse(responseCode = "200", description = "识别成功，data 为字符串：识别出的全部文字，多个文本块之间以换行分隔；未识别到文字时为空字符串")
    public String ocr(
            @Parameter(description = "OCR 补充参数（key / content），当前版本未使用，可不传")
            @ParameterObject OcrParam param,
            @Parameter(description = "待识别的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return bizService.ocr(param,file,request);
    }

    @PostMapping(value = "/ocr/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别文字，返回结构化数组（RapidOcr）",
            description = "使用 RapidOcr 引擎识别图片中的文字，返回每个文本块的文字内容、四点坐标、检测置信度、方向类别与各阶段耗时。")
    @ApiResponse(responseCode = "200", description = "识别成功，data 为文本块数组；未识别到文字时返回空数组")
    public List<TextBlock> jsonOcr(
            @Parameter(description = "待识别的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return bizService.jsonOcr(null,file,request);
    }

    @PostMapping(value = "/word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别文字，返回拼接后的纯文本（DJL）",
            description = "使用 DJL 加载的文字识别模型识别图片，多个文本块拼接为一个字符串返回。"
                    + "需要坐标、字符置信度等结构化信息时请调用 /word/json。")
    @ApiResponse(responseCode = "200", description = "识别成功，data 为字符串：识别出的全部文字；未识别到文字时为空字符串")
    public String ppWorld(
            @Parameter(description = "待识别的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return bizService.ppWorld(null,file,request);
    }

    @PostMapping(value = "/word/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别文字，返回结构化数组（DJL）",
            description = "使用 DJL 文字识别模型识别图片，返回每个文本块的文字内容、逐字符置信度与四点坐标。")
    @ApiResponse(responseCode = "200", description = "识别成功，data 为文本块数组；未识别到文字时返回空数组")
    public List<WordBlock> jsonPpWorld(
            @Parameter(description = "待识别的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return bizService.jsonPpWorld(null,file,request);
    }

    @PostMapping(value = "/yolo8", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别物体，返回多行文本（YOLOv8）",
            description = "使用 YOLOv8 通用模型检测图片中的物体，每条结果拼成一行文本返回，适合人工查看与日志打印。"
                    + "需要程序解析时请调用 /yolo8/json。")
    @ApiResponse(responseCode = "200", description = "识别成功，data 为字符串：每条检测结果一行，包含类别、类别编号、左上角与右下角坐标、置信度；未检测到物体时为空字符串")
    public String yolo(
            @Parameter(description = "待检测的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return bizService.yolo(null,file,request);
    }

    @PostMapping(value = "/yolo8/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别物体，返回结构化数组（YOLOv8）",
            description = "使用 YOLOv8 通用模型检测图片中的物体，返回类别、类别编号、检测框像素坐标与置信度。")
    @ApiResponse(responseCode = "200", description = "识别成功，data 为检测结果数组；未检测到物体时返回空数组")
    public List<Detection> jsonYolo(
            @Parameter(description = "待检测的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return bizService.jsonYolo(null,file,request);
    }

    @PostMapping(value = "/fire/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "火焰烟雾检测（返回结构化数组）",
            description = "使用火焰烟雾专用模型检测图片中的火焰与烟雾，坐标为按图片宽高归一化后的 0~1 数值。")
    @ApiResponse(responseCode = "200", description = "检测成功，data 为检测结果数组（className 为 fire / smoke）；未检测到时返回空数组")
    public List<DetectResult> fireYolo(
            @Parameter(description = "待检测的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return DetectResult.from(bizService.fireYolo(null,file,request));
    }

    @PostMapping(value = "/reflective/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "反光衣检测（返回结构化数组）",
            description = "检测图片中人员是否正确穿着反光衣，坐标为按图片宽高归一化后的 0~1 数值。")
    @ApiResponse(responseCode = "200", description = "检测成功，data 为检测结果数组；未检测到时返回空数组")
    public List<DetectResult> reflective(
            @Parameter(description = "待检测的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return DetectResult.from(bizService.reflective(null,file,request));
    }

    @PostMapping(value = "/construction/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "安全帽检测（返回结构化数组）",
            description = "检测图片中人员是否佩戴安全帽，坐标为按图片宽高归一化后的 0~1 数值。")
    @ApiResponse(responseCode = "200", description = "检测成功，data 为检测结果数组；未检测到时返回空数组")
    public List<DetectResult> construction(
            @Parameter(description = "待检测的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return DetectResult.from(bizService.construction(null,file,request));
    }
}
