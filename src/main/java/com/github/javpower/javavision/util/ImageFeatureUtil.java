package com.github.javpower.javavision.util;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.detect.ImageFeatureDetect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 图片特征获取
 */
public class ImageFeatureUtil {
    private static final Logger logger = LoggerFactory.getLogger(ImageFeatureUtil.class);
    private static volatile ImageFeatureUtil instance;
    private Predictor<Image, float[]> predictor;

    // 单例的私有构造函数
    private ImageFeatureUtil() {
        try {
            ImageFeatureDetect imageFeatureDetect = new ImageFeatureDetect("image_feature.zip");
            Criteria<Image, float[]> criteria = imageFeatureDetect.criteria();
            this.predictor = ModelZoo.loadModel(criteria).newPredictor();
        } catch (IOException | ModelException | OrtException e) {
            logger.error("Failed to initialize Predictor", e);
        }
    }

    // 获取单例实例的方法
    public static ImageFeatureUtil getInstance() {
        if (instance == null) {
            synchronized (ImageFeatureUtil.class) {
                if (instance == null) {
                    instance = new ImageFeatureUtil();
                }
            }
        }
        return instance;
    }

    // 静态的runOcr方法，用于外部调用
    public static float[] runOcr(String path) throws IOException, ModelException, TranslateException, OrtException {
        Path imageFile = Paths.get(path);
        Image image = ImageFactory.getInstance().fromFile(imageFile);
        return runOcr(image);
    }

    public static float[] runOcr(Image image) throws ModelException, TranslateException, OrtException {
        // 通过getInstance()获取单例实例，然后调用其predict方法
        return getInstance().predict(image);
    }

    // 非静态的predict方法，仅供单例内部使用
    private float[] predict(Image image) throws ModelException, TranslateException, OrtException {
        return predictor.predict(image);
    }

    // 关闭Predictor的方法
    public void close() {
        if (predictor != null) {
            try {
                predictor.close();
            } catch (Exception e) {
                logger.error("Failed to close Predictor", e);
            }
        }
    }

    // 添加关闭钩子
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> getInstance().close()));
    }
}
