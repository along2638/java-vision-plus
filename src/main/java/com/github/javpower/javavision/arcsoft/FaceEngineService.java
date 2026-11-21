package com.github.javpower.javavision.arcsoft;

import cn.hutool.core.collection.CollectionUtil;
import com.arcsoft.face.*;
import com.arcsoft.face.enums.DetectMode;
import com.arcsoft.face.enums.DetectOrient;
import com.arcsoft.face.toolkit.ImageFactory;
import com.arcsoft.face.toolkit.ImageInfo;
import com.github.javpower.javavision.arcsoft.entity.FaceDetectObject;
import com.github.javpower.javavision.arcsoft.entity.ProcessInfo;
import com.github.javpower.javavision.framework.exception.BusinessException;
import com.github.javpower.javavision.util.ArcSoftFaceUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FaceEngineService {

    @Value("${config.arcface-sdk.app-id}")
    public String appId;

    @Value("${config.arcface-sdk.sdk-key}")
    public String sdkKey;

    @Value("${config.arcface-sdk.detect-pool-size}")
    public Integer detectPooSize;

    @Value("${config.arcface-sdk.compare-pool-size}")
    public Integer comparePooSize;

    //通用人脸识别引擎池
    private GenericObjectPool<FaceEngine> faceEngineGeneralPool;

    //人脸比对引擎池
    private GenericObjectPool<FaceEngine> faceEngineComparePool;

    @PostConstruct
    public void init() {
        GenericObjectPoolConfig detectPoolConfig = new GenericObjectPoolConfig();
        detectPoolConfig.setMaxIdle(detectPooSize);
        detectPoolConfig.setMaxTotal(detectPooSize);
        detectPoolConfig.setMinIdle(detectPooSize);
        detectPoolConfig.setLifo(false);
        EngineConfiguration detectCfg = new EngineConfiguration();
        FunctionConfiguration detectFunctionCfg = new FunctionConfiguration();
        detectFunctionCfg.setSupportFaceDetect(true);//开启人脸检测功能
        detectFunctionCfg.setSupportFaceRecognition(true);//开启人脸识别功能
        detectFunctionCfg.setSupportAge(true);//开启年龄检测功能
        detectFunctionCfg.setSupportGender(true);//开启性别检测功能
        detectFunctionCfg.setSupportLiveness(true);//开启活体检测功能
        detectCfg.setFunctionConfiguration(detectFunctionCfg);
        detectCfg.setDetectMode(DetectMode.ASF_DETECT_MODE_IMAGE);//图片检测模式，如果是连续帧的视频流图片，那么改成VIDEO模式
        detectCfg.setDetectFaceOrientPriority(DetectOrient.ASF_OP_0_ONLY);//人脸旋转角度
        faceEngineGeneralPool = new GenericObjectPool(new FaceEngineFactory(appId, sdkKey, null,detectCfg), detectPoolConfig);//底层库算法对象池
        //初始化特征比较线程池
        GenericObjectPoolConfig comparePoolConfig = new GenericObjectPoolConfig();
        comparePoolConfig.setMaxIdle(comparePooSize);
        comparePoolConfig.setMaxTotal(comparePooSize);
        comparePoolConfig.setMinIdle(comparePooSize);
        comparePoolConfig.setLifo(false);
        EngineConfiguration compareCfg = new EngineConfiguration();
        FunctionConfiguration compareFunctionCfg = new FunctionConfiguration();
        compareFunctionCfg.setSupportFaceRecognition(true);//开启人脸识别功能
        compareCfg.setFunctionConfiguration(compareFunctionCfg);
        compareCfg.setDetectMode(DetectMode.ASF_DETECT_MODE_IMAGE);//图片检测模式，如果是连续帧的视频流图片，那么改成VIDEO模式
        compareCfg.setDetectFaceOrientPriority(DetectOrient.ASF_OP_0_ONLY);//人脸旋转角度
        faceEngineComparePool = new GenericObjectPool(new FaceEngineFactory(appId, sdkKey, null,compareCfg), comparePoolConfig);//底层库算法对象池
    }


    /**
     * 获取人脸特征
     * @param
     * @return
     * @throws IOException
     */
    public List<FaceDetectObject> getFaceDetect(String path) throws IOException {
        ImageInfo imageInfo = ImageFactory.getRGBData(new File(path));
        return getFaceDetect(imageInfo);
    }
    public List<FaceDetectObject> getFaceDetect(MultipartFile file) throws IOException {
        InputStream inputStream = file.getInputStream();
        ImageInfo imageInfo = ImageFactory.getRGBData(inputStream);
        return getFaceDetect(imageInfo);
    }
    private List<FaceDetectObject> getFaceDetect(ImageInfo imageInfo){
        List<FaceDetectObject> faceDetectObjects=new ArrayList<>();
        List<FaceFeature> list=new ArrayList<>();
        List<FaceInfo> faceInfoList = detectFaces(imageInfo);
        if (CollectionUtil.isEmpty(faceInfoList)) {
            throw new BusinessException("照片1未检测到人脸");
        }
        for (FaceInfo faceInfo : faceInfoList) {
            FaceFeature faceFeature=new FaceFeature();
            byte[] bytes = extractFaceFeature(imageInfo, faceInfo);
            faceFeature.setFeatureData(bytes);
            list.add(faceFeature);
        }
        if (CollectionUtil.isNotEmpty(faceInfoList)) {
            List<ProcessInfo> process = process(imageInfo, faceInfoList);
            for (int i = 0; i < faceInfoList.size(); i++) {
                FaceDetectObject faceDetectObject = new FaceDetectObject();
                FaceInfo faceInfo = faceInfoList.get(i);
                faceDetectObject.setRect(faceInfo.getRect());
                faceDetectObject.setOrient(faceInfo.getOrient());
                faceDetectObject.setFaceId(faceInfo.getFaceId());
                if (CollectionUtil.isNotEmpty(process)) {
                    ProcessInfo processInfo = process.get(i);
                    faceDetectObject.setAge(processInfo.getAge());
                    faceDetectObject.setGender(processInfo.getGender());
                    faceDetectObject.setLiveness(processInfo.getLiveness());
                }
                FaceFeature faceFeature=new FaceFeature();
                byte[] bytes = extractFaceFeature(imageInfo, faceInfo);
                faceFeature.setFeatureData(bytes);
                List<Float> vector = ArcSoftFaceUtil.convertToFloatVector(bytes, false);
                faceDetectObject.setFaceFeature(vector);
                faceDetectObjects.add(faceDetectObject);
            }
        }
        return faceDetectObjects;
    }
    /**
     * 人脸检测
     * @param imageInfo
     * @return
     */
    private List<FaceInfo> detectFaces(ImageInfo imageInfo) {

        FaceEngine faceEngine = null;
        try {
            faceEngine = faceEngineGeneralPool.borrowObject();
            if (faceEngine == null) {
                throw new BusinessException("获取引擎失败");
            }
            //人脸检测得到人脸列表
            List<FaceInfo> faceInfoList = new ArrayList<FaceInfo>();
            //人脸检测
            int errorCode = faceEngine.detectFaces(imageInfo.getImageData(), imageInfo.getWidth(), imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList);
            if (errorCode == 0) {
                return faceInfoList;
            } else {
                log.error("人脸检测失败，errorCode：" + errorCode);
            }

        } catch (Exception e) {
            log.error("", e);
        } finally {
            if (faceEngine != null) {
                //释放引擎对象
                faceEngineGeneralPool.returnObject(faceEngine);
            }
        }

        return null;

    }

    /**
     * 人脸特征
     *
     * @param imageInfo
     * @return
     */
    private byte[] extractFaceFeature(ImageInfo imageInfo, FaceInfo faceInfo) {

        FaceEngine faceEngine = null;
        try {
            faceEngine = faceEngineGeneralPool.borrowObject();
            if (faceEngine == null) {
                throw new BusinessException("获取引擎失败");
            }

            FaceFeature faceFeature = new FaceFeature();
            //提取人脸特征
            int errorCode = faceEngine.extractFaceFeature(imageInfo.getImageData(), imageInfo.getWidth(), imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfo, faceFeature);
            if (errorCode == 0) {
                return faceFeature.getFeatureData();
            } else {
                log.error("特征提取失败，errorCode：" + errorCode);
            }

        } catch (Exception e) {
            log.error("", e);
        } finally {
            if (faceEngine != null) {
                //释放引擎对象
                faceEngineGeneralPool.returnObject(faceEngine);
            }
        }

        return null;

    }

    private List<ProcessInfo> process(ImageInfo imageInfo, List<FaceInfo> faceInfoList) {
        FaceEngine faceEngine = null;
        try {
            //获取引擎对象
            faceEngine = faceEngineGeneralPool.borrowObject();
            if (faceEngine == null) {
                throw new BusinessException("获取引擎失败");
            }


            int errorCode = faceEngine.process(imageInfo.getImageData(), imageInfo.getWidth(), imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList, FunctionConfiguration.builder().supportAge(true).supportGender(true).supportLiveness(true).build());
            if (errorCode == 0) {
                List<ProcessInfo> processInfoList = Lists.newLinkedList();

                //性别列表
                List<GenderInfo> genderInfoList = new ArrayList<GenderInfo>();
                faceEngine.getGender(genderInfoList);

                //年龄列表
                List<AgeInfo> ageInfoList = new ArrayList<AgeInfo>();
                faceEngine.getAge(ageInfoList);
                //活体结果列表
                List<LivenessInfo> livenessInfoList = new ArrayList<LivenessInfo>();
                faceEngine.getLiveness(livenessInfoList);


                for (int i = 0; i < genderInfoList.size(); i++) {
                    ProcessInfo processInfo = new ProcessInfo();
                    processInfo.setGender(genderInfoList.get(i).getGender());
                    processInfo.setAge(ageInfoList.get(i).getAge());
                    processInfo.setLiveness(livenessInfoList.get(i).getLiveness());
                    processInfoList.add(processInfo);
                }
                return processInfoList;

            }


        } catch (Exception e) {
            log.error("", e);
        } finally {
            if (faceEngine != null) {
                //释放引擎对象
                faceEngineGeneralPool.returnObject(faceEngine);
            }
        }

        return null;

    }
}
