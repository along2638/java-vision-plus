package com.github.javpower.javavision.vectorex.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.arcsoft.FaceEngineService;
import com.github.javpower.javavision.arcsoft.entity.FaceDetectObject;
import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.util.FileUtil;
import com.github.javpower.javavision.vectorex.mapper.FaceVectoRexMapper;
import com.github.javpower.javavision.vectorex.model.Face;
import io.github.javpower.vectorexbootstater.core.VectoRexResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class FaceVectoRexService {
    @Autowired
    private FaceVectoRexMapper faceVectoRexMapper;
    @Autowired
    private  FileUtil fileUtil;
    @Autowired
    private FaceEngineService faceEngineService;

    public void add(String faceId,String personName,MultipartFile file) throws IOException, ModelException, TranslateException, OrtException {
        Face face=new Face();
        String path = fileUtil.getPath(file);
        List<FaceDetectObject> faceDetect = faceEngineService.getFaceDetect(path);
        face.setPersonId(faceId);
        face.setUrl(path);
        face.setPersonName(personName);
        face.setFaceVector(faceDetect.get(0).getFaceFeature());
        faceVectoRexMapper.insert(face);

    }
    //图片及展示k个结果
    public Face search(MultipartFile file) throws IOException{
        List<FaceDetectObject> faceDetect = faceEngineService.getFaceDetect(file);
        List<VectoRexResult<Face>> data = faceVectoRexMapper.queryWrapper()
                .vector(Face::getFaceVector, faceDetect.get(0).getFaceFeature())
                .topK(1).query();
        for (VectoRexResult<Face> datum : data) {
            Face entity = datum.getEntity();
           return entity;
        }
        return null;
    }

    public void update(FaceParam param, MultipartFile file, HttpServletRequest request) throws IOException {
        String path = fileUtil.getPath(file);
        List<FaceDetectObject> faceDetect = faceEngineService.getFaceDetect(path);
        Face face=new Face();
        face.setPersonId(param.getPersonId());
        face.setPersonName(param.getPersonName());
        face.setFaceVector(faceDetect.get(0).getFaceFeature());
        face.setUrl(path);
        faceVectoRexMapper.updateById(face);
    }

    public void del(FaceParam param ,HttpServletRequest request) {
        faceVectoRexMapper.removeById(param.getPersonId());
    }
}
