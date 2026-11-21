package com.github.javpower.javavision.controller;


import com.github.javpower.javavision.db.ImageRecord;
import com.github.javpower.javavision.es.response.SearchResult;
import com.github.javpower.javavision.es.service.ImageSearchService;
import com.github.javpower.javavision.framework.response.PageResult;
import com.github.javpower.javavision.milvus.service.ImageMilvusService;
import com.github.javpower.javavision.service.IImageService;
import com.github.javpower.javavision.service.ImageRecordService;
import com.github.javpower.javavision.vectorex.service.ImageVectoRexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 图库与以图搜图接口。
 *
 * @author gc.x
 * @date 2023/11/11
 **/
@Slf4j
@Validated
@RestController
@RequestMapping("/image")
@Tag(name = "以图搜图应用", description = "图片入库与相似图片检索，向量库由配置项 image.service 决定（vectorex / milvus / es），"
        + "图片原文件存对象存储，元数据存 PostgreSQL。所有接口返回统一结构 ApiResult："
        + "{code 响应码, success 是否成功, msg 提示信息, data 业务数据, time 响应时间}")
public class ImageController {

    private static final int DEFAULT_TOP_K = 10;

    private final IImageService imageService;

    private final ImageRecordService imageRecordService;

    public ImageController(Environment environment,
                           ImageSearchService imageSearchService,
                           ImageMilvusService imageMilvusService,
                           ImageVectoRexService imageVectoRexService,
                           ImageRecordService imageRecordService) {
        this.imageRecordService = imageRecordService;
        // 由配置项 image.service 选一个向量库实现，默认 vectorex
        this.imageService = initializeService(environment.getProperty("image.service", "vectorex"),
                imageSearchService, imageMilvusService, imageVectoRexService);
    }

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "图片入库",
            description = "上传图片，提取图片向量写入向量库，原图存入对象存储，元数据（含 MD5、向量维度）写入 PostgreSQL。"
                    + "相同 MD5 的图片会复用已缓存的向量，避免重复推理。")
    @ApiResponse(responseCode = "200", description = "入库成功，data 为 null")
    public void add(
            @Parameter(description = "图片业务编号，由调用方指定，未删除记录内唯一", required = true, example = "img-0001")
            @NotBlank(message = "图片编号不能为空")
            @Size(max = 64, message = "图片编号长度不能超过 64 个字符")
            @RequestParam(value = "imageId") String imageId,
            @Parameter(description = "图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        imageService.add(imageId,file);
    }

    @PostMapping(value = "/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "以图搜图（返回相似度最高的 K 条）",
            description = "上传图片计算特征向量，在向量库中做相似度检索。")
    @ApiResponse(responseCode = "200", description = "检索成功，data 为命中结果列表，按相似度从高到低排序；库为空时返回空数组")
    public List<SearchResult> search(
            @Parameter(description = "返回条数，不传或小于等于 0 时默认 10 条", example = "10")
            @Max(value = 100, message = "返回条数不能超过 100")
            @RequestParam(value = "k", required = false) Integer k,
            @Parameter(description = "待检索的图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        // 未指定条数时默认返回 10 条，避免实现类里 topK(null) 报错
        int topK = (k == null || k <= 0) ? DEFAULT_TOP_K : k;
        return imageService.search(file.getInputStream(), topK);
    }

    @PostMapping("/del")
    @Operation(summary = "删除图片",
            description = "按图片业务编号删除向量库中的向量、对象存储中的原图，并将 PostgreSQL 元数据标记为已删除（逻辑删除）。")
    @ApiResponse(responseCode = "200", description = "删除成功，data 为 null")
    public void del(
            @Parameter(description = "图片业务编号，要删除的图片唯一标识", required = true, example = "img-0001")
            @NotBlank(message = "图片编号不能为空")
            @Size(max = 64, message = "图片编号长度不能超过 64 个字符")
            @RequestParam(value = "imageId") String imageId,
            HttpServletRequest request) throws Exception {
        imageService.del(imageId);
    }

    @GetMapping("/list")
    @Operation(summary = "图库列表（分页）",
            description = "从 PostgreSQL 元数据表分页查询未删除的图片记录，不返回向量本身。")
    @ApiResponse(responseCode = "200", description = "查询成功，data 为分页结果对象")
    public PageResult<ImageRecord> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @Min(value = 1, message = "页码从 1 开始")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页条数，最大 200", example = "10")
            @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 200, message = "每页条数不能超过 200")
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return PageResult.of(imageRecordService.page(page, size));
    }

    /**
     * 按配置项选择向量库实现，未命中时回退到 ES。
     */
    private IImageService initializeService(String impl, ImageSearchService imageSearchService,
                                            ImageMilvusService imageMilvusService,
                                            ImageVectoRexService imageVectoRexService) {
        if ("milvus".equalsIgnoreCase(impl)) {
            return imageMilvusService;
        }
        if ("vectorex".equalsIgnoreCase(impl)) {
            return imageVectoRexService;
        }
        return imageSearchService;
    }
}
