package com.github.javpower.javavision.milvus.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.arcsoft.FaceEngineService;
import com.github.javpower.javavision.arcsoft.entity.FaceDetectObject;
import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.milvus.model.Face;
import com.github.javpower.javavision.milvus.model.FaceMilvusMapper;
import com.github.javpower.javavision.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.milvus.plus.model.vo.MilvusResp;
import org.dromara.milvus.plus.model.vo.MilvusResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class FaceMilvusService{

    private final FaceMilvusMapper faceMilvusMapper;

    private final FileUtil fileUtil;

    private final FaceEngineService faceEngineService;

    public FaceMilvusService(FaceMilvusMapper faceMilvusMapper, FileUtil fileUtil,
                             FaceEngineService faceEngineService) {
        this.faceMilvusMapper = faceMilvusMapper;
        this.fileUtil = fileUtil;
        this.faceEngineService = faceEngineService;
    }

    public void add(String faceId,String personName,MultipartFile file) throws IOException, ModelException, TranslateException, OrtException {
        Face face=new Face();
        // 人脸图片存入对象存储，向量库保存 URL 与特征
        String url = fileUtil.upload(file, "face");
        List<FaceDetectObject> faceDetect = detectFace(url);
        face.setPersonId(faceId);
        face.setUrl(url);
        face.setPersonName(personName);
        face.setFaceVector(faceDetect.get(0).getFaceFeature());
        faceMilvusMapper.insert(face);
    }
    //图片及展示k个结果
    public Face search(MultipartFile file) throws IOException{
        List<FaceDetectObject> faceDetect = faceEngineService.getFaceDetect(file);
        MilvusResp<List<MilvusResult<Face>>> query = faceMilvusMapper.queryWrapper()
                .vector(Face::getFaceVector,faceDetect.get(0).getFaceFeature())
                .topK(1).query();
        List<MilvusResult<Face>> data = query.getData();
        for (MilvusResult<Face> datum : data) {
            Face entity = datum.getEntity();
           return entity;
        }
        return null;
    }

    public void update(FaceParam param, MultipartFile file, HttpServletRequest request) throws IOException {
        String url = fileUtil.upload(file, "face");
        List<FaceDetectObject> faceDetect = detectFace(url);
        Face face=new Face();
        face.setPersonId(param.getPersonId());
        face.setPersonName(param.getPersonName());
        face.setFaceVector(faceDetect.get(0).getFaceFeature());
        face.setUrl(url);
        faceMilvusMapper.updateById(face);
    }

    /**
     * 虹软 SDK 只接受本地文件：MinIO 模式下先物化到本地，用完即清理。
     */
    private List<FaceDetectObject> detectFace(String url) throws IOException {
        Path local = fileUtil.materialize(url);
        try {
            return faceEngineService.getFaceDetect(local.toString());
        } finally {
            fileUtil.release(local);
        }
    }

    public void del(FaceParam param ,HttpServletRequest request) {
        faceMilvusMapper.removeById(param.getPersonId());
    }
}
