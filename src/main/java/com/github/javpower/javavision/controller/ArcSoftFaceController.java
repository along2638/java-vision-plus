package com.github.javpower.javavision.controller;


import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.milvus.model.Face;
import com.github.javpower.javavision.milvus.service.FaceMilvusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 虹软人脸 + Milvus 接口。
 *
 * @author gc.x
 * @date 2023/6/18
 **/
@Slf4j
@Validated
@RestController
@RequestMapping("/arc/soft/face")
@Tag(name = "虹软人脸应用", description = "虹软 SDK 提取特征 + Milvus 存储的人脸库，与 /face 接口相互独立，需要部署 Milvus 才能使用。"
        + "所有接口返回统一结构 ApiResult：{code 响应码, success 是否成功, msg 提示信息, data 业务数据, time 响应时间}")
public class ArcSoftFaceController {

    private final FaceMilvusService faceMilvusService;

    public ArcSoftFaceController(FaceMilvusService faceMilvusService) {
        this.faceMilvusService = faceMilvusService;
    }

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "添加人脸",
            description = "提取上传图片的人脸特征并写入 Milvus 集合 face_collection，personId 作为主键，已存在时会覆盖。")
    @ApiResponse(responseCode = "200", description = "添加成功，data 为 null")
    public void addFace(
            @Parameter(description = "人员编号，Milvus 集合主键", required = true, example = "0000")
            @NotBlank(message = "人员编号不能为空")
            @Size(max = 64, message = "人员编号长度不能超过 64 个字符")
            @RequestParam(value = "personId") String personId,
            @Parameter(description = "人员姓名", required = true, example = "张三")
            @NotBlank(message = "人员姓名不能为空")
            @Size(max = 128, message = "人员姓名长度不能超过 128 个字符")
            @RequestParam(value = "personName") String personName,
            @Parameter(description = "人脸图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        FaceParam param=new FaceParam();
        param.setPersonId(personId);
        param.setPersonName(personName);
        faceMilvusService.add(param.getPersonId(),param.getPersonName(),file);
    }

    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新人脸",
            description = "按 personId 覆盖更新 Milvus 中的人员姓名、图片地址与人脸特征向量。")
    @ApiResponse(responseCode = "200", description = "更新成功，data 为 null")
    public void updateFace(
            @Parameter(description = "人员编号，已存在则更新", required = true, example = "0000")
            @NotBlank(message = "人员编号不能为空")
            @Size(max = 64, message = "人员编号长度不能超过 64 个字符")
            @RequestParam(value = "personId") String personId,
            @Parameter(description = "人员姓名", required = true, example = "张三")
            @NotBlank(message = "人员姓名不能为空")
            @Size(max = 128, message = "人员姓名长度不能超过 128 个字符")
            @RequestParam(value = "personName") String personName,
            @Parameter(description = "新的人脸图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        FaceParam param=new FaceParam();
        param.setPersonId(personId);
        param.setPersonName(personName);
        faceMilvusService.update(param,file,request);
    }

    @PostMapping("/del")
    @Operation(summary = "删除人脸",
            description = "按 personId 删除 Milvus 集合中的人员记录，同时清理对象存储中的人脸图片。")
    @ApiResponse(responseCode = "200", description = "删除成功，data 为 null")
    public void delFace(
            @Parameter(description = "人员编号，要删除的人员主键", required = true, example = "0000")
            @NotBlank(message = "人员编号不能为空")
            @Size(max = 64, message = "人员编号长度不能超过 64 个字符")
            @RequestParam(value = "personId") String personId,
            HttpServletRequest request) throws Exception {
        FaceParam param=new FaceParam();
        param.setPersonId(personId);
        faceMilvusService.del(param,request);
    }

    @PostMapping(value = "/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "人脸查询（Milvus 向量检索）",
            description = "上传人脸图片提取特征，在 Milvus 中做向量相似度检索，返回最相似的一条人员记录（含人员编号、姓名、图片地址与特征向量）。"
                    + "未达相似度阈值时 data 为 null。")
    @ApiResponse(responseCode = "200", description = "检索成功，data 为命中的人员记录；未命中时 data 为 null")
    public Face searchFace(
            @Parameter(description = "待检索的人脸图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return faceMilvusService.search(file);
    }
}
