package com.github.javpower.javavision.detect.translator;

import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import com.github.javpower.javavision.util.DjlHandlerUtil;
import com.github.javpower.javavision.util.InferenceDevice;
import com.github.javpower.javavision.util.JarFileUtils;
import com.github.javpower.javavision.util.OpenCVExtendUtil;
import com.github.javpower.javavision.util.PathConstants;
import lombok.extern.slf4j.Slf4j;

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
        // 模型加载走缓存后这两行每次请求都会打印，降为 debug 避免刷日志
        log.debug("模型本地路径:{}",modelPath);
        modelPath= DjlHandlerUtil.getFixedModelPath(modelPath);
        log.debug("模型 file URL:{}",modelPath);
        Criteria<Image, T> criteria =
                Criteria.builder()
                        .setTypes(Image.class, getClassOfT())
                        .optModelUrls(modelPath)
                        .optTranslator(translator)
                        .optEngine(getEngine()) // Use PyTorch engine
                        .optDevice(InferenceDevice.device())
                        .optProgress(new ProgressBar())
                        .build();
        return criteria;
    }

    /**
     * 按当前参数构建 translator，供每次请求创建 Predictor 使用（translator 绑定了图片尺寸）。
     */
    public Translator<Image, T> translator() {
        return getTranslator(arguments);
    }

    protected abstract Translator<Image, T> getTranslator(Map<String, Object> arguments);

    // 获取 T 类型的函数
    protected abstract Class<T> getClassOfT();

    protected abstract String getEngine();

}

