package com.github.javpower.javavision.controller;


import com.github.javpower.javavision.es.response.SearchResult;
import com.github.javpower.javavision.es.service.ImageSearchService;
import com.github.javpower.javavision.milvus.service.ImageMilvusService;
import com.github.javpower.javavision.service.IImageService;
import com.github.javpower.javavision.vectorex.service.ImageVectoRexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author gc.x
 * @date 2023/11/11
 **/
@Slf4j
@RestController
@RequestMapping("/image")
@Tag(name = "以图搜图应用")
public class ImageController implements EnvironmentAware {

    private IImageService imageService;
    private String impl;

    public ImageController(Environment environment,ImageSearchService imageSearchService,ImageMilvusService imageMilvusService,ImageVectoRexService imageVectoRexService) {
        // 从环境对象中获取配置项的值
        this.impl = environment.getProperty("image.service");
        initializeService(imageSearchService, imageMilvusService,imageVectoRexService);
    }
    @PostMapping("/add")
    @Operation(summary = "添加")
    public void add(String imageId, MultipartFile file, HttpServletRequest request) throws Exception {
        imageService.add(imageId,file);
    }
    @PostMapping("/search")
    @Operation(summary = "查询k个结果")
    public List<SearchResult> search(Integer k, MultipartFile file, HttpServletRequest request) throws Exception {
        return imageService.search(file.getInputStream(),k);
    }
    @PostMapping("/del")
    @Operation(summary = "删除")
    public void del(String imageId, HttpServletRequest request) throws Exception {
        imageService.del(imageId);
    }

    @Override
    public void setEnvironment(Environment environment) {

    }
    // 根据配置项初始化服务
    private void initializeService(ImageSearchService imageSearchService, ImageMilvusService imageMilvusService, ImageVectoRexService imageVectoRexService) {
        if ("milvus".equalsIgnoreCase(impl)) {
            this.imageService = imageMilvusService;
        } if ("vectorex".equalsIgnoreCase(impl)) {
            this.imageService = imageVectoRexService;
        } else {
            this.imageService = imageSearchService;
        }
    }
}
