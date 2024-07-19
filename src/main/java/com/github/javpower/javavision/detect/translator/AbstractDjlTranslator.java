package com.github.javpower.javavision.detect.translator;

import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import com.github.javpower.javavision.util.DjlHandlerUtil;
import com.github.javpower.javavision.util.JarFileUtils;
import com.github.javpower.javavision.util.OpenCVExtendUtil;
import com.github.javpower.javavision.util.PathConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


import java.io.File;
import java.io.IOException;
import java.util.Map;

@Slf4j
public abstract class AbstractDjlTranslator<T> {

    public String modelName;

    public Map<String, Object> arguments;

    static {
        // 加载opencv动态库，
        //System.load(ClassLoader.getSystemResource("lib/opencv_java470-无用.dll").getPath());
//        nu.pattern.OpenCV.loadLocally();
        OpenCVExtendUtil.init();
    }

    public AbstractDjlTranslator(String modelName, Map<String, Object> arguments) {
        this.modelName = modelName;
        this.arguments=arguments;
    }

    public Criteria<Image, T> criteria() {
        Translator<Image, T> translator = getTranslator(arguments);
        try {
            JarFileUtils.copyFileFromJar("/onnx/models/" + modelName, PathConstants.ONNX, null, false, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        String model_path = PathConstants.TEMP_DIR + PathConstants.ONNX + "/" + modelName;
        String modelPath = PathConstants.TEMP_DIR + File.separator+PathConstants.ONNX_NAME+ File.separator + modelName;
        log.info("路径修改前:{}",modelPath);
        modelPath= DjlHandlerUtil.getFixedModelPath(modelPath);
        log.info("路径修改后:{}",modelPath);
        Criteria<Image, T> criteria =
                Criteria.builder()
                        .setTypes(Image.class, getClassOfT())
                        .optModelUrls(modelPath)
                        .optTranslator(translator)
                        .optEngine(getEngine()) // Use PyTorch engine
                        .optProgress(new ProgressBar())
                        .build();
        return criteria;
    }

    protected abstract Translator<Image, T> getTranslator(Map<String, Object> arguments);

    // 获取 T 类型的函数
    protected abstract Class<T> getClassOfT();

    protected abstract String getEngine();

}

