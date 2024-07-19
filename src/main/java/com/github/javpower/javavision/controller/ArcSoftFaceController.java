package com.github.javpower.javavision.controller;


import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.milvus.model.Face;
import com.github.javpower.javavision.milvus.service.FaceMilvusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * @author gc.x
 * @date 2023/6/18
 **/
@Slf4j
@RestController
@RequestMapping("/arc/soft/face")
@Tag(name = "虹软人脸应用")
public class ArcSoftFaceController {

    @Autowired
    private FaceMilvusService faceMilvusService;
    @PostMapping("/add")
    @Operation(summary = "添加人脸")
    public void addFace(FaceParam param, @RequestParam(value = "file")MultipartFile file, HttpServletRequest request) throws Exception {
        faceMilvusService.add(param.getPersonId(),param.getPersonName(),file);
    }
    @PostMapping("/update")
    @Operation(summary = "更新人脸")
    public void updateFace(FaceParam param,  @RequestParam(value = "file")MultipartFile file, HttpServletRequest request) throws Exception {
        faceMilvusService.update(param,file,request);
    }
    @PostMapping("/del")
    @Operation(summary = "删除人脸")
    public void delFace(FaceParam param, HttpServletRequest request) throws Exception {
        faceMilvusService.del(param,request);
    }
    @PostMapping("/search")
    @Operation(summary = "人脸查询")
    public Face searchFace( @RequestParam(value = "file")MultipartFile file, HttpServletRequest request) throws Exception {
        return faceMilvusService.search(file);
    }
}
