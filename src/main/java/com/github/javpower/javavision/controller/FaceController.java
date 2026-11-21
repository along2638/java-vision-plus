package com.github.javpower.javavision.controller;


import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.entity.PersonObject;
import com.github.javpower.javavision.service.FaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 人脸库接口。
 *
 * @author gc.x
 * @date 2023/6/18
 **/
@Slf4j
@Validated
@RestController
@RequestMapping("/face")
@Tag(name = "人脸应用", description = "人脸库的增删改查，基于虹软算法提取 256 维特征。"
        + "所有接口返回统一结构 ApiResult：{code 响应码, success 是否成功, msg 提示信息, data 业务数据, time 响应时间}")
public class FaceController {

    private final FaceService faceService;

    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "添加人脸",
            description = "上传一张人脸图片并绑定人员编号与姓名。图片会存入对象存储，特征向量写入向量库，人员信息写入 PostgreSQL。"
                    + "同一 personId 已存在时请调用更新接口。")
    @ApiResponse(responseCode = "200", description = "添加成功，data 为 null（返回体只有 code/success/msg/time）")
    public void addFace(
            @Parameter(description = "人员编号，业务唯一标识，不可为空", required = true, example = "0000")
            @NotBlank(message = "人员编号不能为空")
            @Size(max = 64, message = "人员编号长度不能超过 64 个字符")
            @RequestParam(value = "personId") String personId,
            @Parameter(description = "人员姓名，不可为空", required = true, example = "张三")
            @NotBlank(message = "人员姓名不能为空")
            @Size(max = 128, message = "人员姓名长度不能超过 128 个字符")
            @RequestParam(value = "personName") String personName,
            @Parameter(description = "人脸图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        FaceParam param=new FaceParam();
        param.setPersonId(personId);
        param.setPersonName(personName);
        faceService.addFace(param,file,request);
    }

    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新人脸",
            description = "按 personId 覆盖更新人脸图片、姓名与特征向量。personId 不存在时按新增处理。")
    @ApiResponse(responseCode = "200", description = "更新成功，data 为 null")
    public void updateFace(
            @Parameter(description = "人员编号，已存在则更新，不存在则新增", required = true, example = "0000")
            @NotBlank(message = "人员编号不能为空")
            @Size(max = 64, message = "人员编号长度不能超过 64 个字符")
            @RequestParam(value = "personId") String personId,
            @Parameter(description = "人员姓名，不可为空", required = true, example = "张三")
            @NotBlank(message = "人员姓名不能为空")
            @Size(max = 128, message = "人员姓名长度不能超过 128 个字符")
            @RequestParam(value = "personName") String personName,
            @Parameter(description = "新的人脸图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        FaceParam param=new FaceParam();
        param.setPersonId(personId);
        param.setPersonName(personName);
        faceService.updateFace(param,file,request);
    }

    @PostMapping("/del")
    @Operation(summary = "删除人脸",
            description = "按 personId 逻辑删除人员及其特征向量，同时清理缓存在 Redis 中的人脸特征列表。")
    @ApiResponse(responseCode = "200", description = "删除成功，data 为 null")
    public void delFace(
            @Parameter(description = "人员编号，要删除的人员唯一标识", required = true, example = "0000")
            @NotBlank(message = "人员编号不能为空")
            @Size(max = 64, message = "人员编号长度不能超过 64 个字符")
            @RequestParam(value = "personId") String personId,
            HttpServletRequest request) throws Exception {
        FaceParam param=new FaceParam();
        param.setPersonId(personId);
        faceService.delFace(param,null,request);
    }

    @PostMapping(value = "/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "人脸查询（1:N 检索）",
            description = "上传一张人脸图片，与库中全部人脸特征逐一比对，返回最相似的人员。库为空或未达相似度阈值时 data 为 null。")
    @ApiResponse(responseCode = "200", description = "检索成功，data 为命中的人员信息；未命中时 data 为 null")
    public PersonObject searchFace(
            @Parameter(description = "待检索的人脸图片文件，支持 jpg/jpeg/png，表单字段名固定为 file", required = true)
            @RequestPart(value = "file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        return faceService.searchFace(null,file,request);
    }

    @GetMapping("/list")
    @Operation(summary = "人脸库列表",
            description = "分页无关，一次性返回 PostgreSQL 中未删除的全部人员。为减少数据传输，返回结果不包含 feature 特征向量字段。")
    @ApiResponse(responseCode = "200", description = "查询成功，data 为人员列表；库为空时返回空数组")
    public List<FaceParam> list() {
        return faceService.listAll();
    }
}
