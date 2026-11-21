package com.github.javpower.javavision.util;

import ai.djl.MalformedModelException;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.detect.FaceDetect;
import com.github.javpower.javavision.detect.FaceFeatureDetect;
import com.github.javpower.javavision.entity.FaceObject;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FaceDetectUtil {
    /** 人脸框最小边长（像素）：低于此值的框基本是误检，且放大到 160×160 后没有判别力 */
    private static final int MIN_FACE_SIZE = 20;

    public static List<FaceObject> faceDetect(String imagePath)
            throws TranslateException, IOException, ModelNotFoundException, MalformedModelException, OrtException {
        Path imageFile = Paths.get(imagePath);
        BufferedImage image = ImageIO.read(imageFile.toFile());
        Map<String,Object> param=new HashMap<>();
        param.put("topK",200);
        param.put("confThresh",0.85);
        param.put("nmsThresh",0.45);
        FaceDetect faceDetect = new FaceDetect(param);
        Criteria<Image, DetectedObjects> criteria = faceDetect.criteria();
        // 模型常驻内存，Predictor 每次请求新建（translator 与 Predictor 都不是线程安全的）
        ZooModel<Image, DetectedObjects> model = loadModel(faceDetect.modelName, criteria);
        try (Predictor<Image, DetectedObjects> predictor = model.newPredictor(faceDetect.translator())) {
            Image djlImg = ImageFactory.getInstance().fromImage(image);
            DetectedObjects detections = predictor.predict(djlImg);
            List<DetectedObjects.DetectedObject> list = detections.items();
            List<FaceObject> faceObjects = new ArrayList<>();
            for (DetectedObjects.DetectedObject detectedObject : list) {
                BoundingBox box = detectedObject.getBoundingBox();
                Rectangle rectangle = box.getBounds();
                // 抠人脸图
                Rectangle subImageRect =
                        FaceUtil.getSubImageRect(
                                image, rectangle, djlImg.getWidth(), djlImg.getHeight(), 0f);
                int x = (int) (subImageRect.getX());
                int y = (int) (subImageRect.getY());
                int w = (int) (subImageRect.getWidth());
                int h = (int) (subImageRect.getHeight());
                // 钳制到图片范围内：检测框贴边时 x+w 会越界，getSubimage 直接抛 RasterFormatException
                x = Math.max(0, Math.min(x, image.getWidth() - 1));
                y = Math.max(0, Math.min(y, image.getHeight() - 1));
                w = Math.min(w, image.getWidth() - x);
                h = Math.min(h, image.getHeight() - y);
                // 过小的框多为误检，放大到 160×160 也只是噪声，跳过而不是让整次请求失败
                if (w < MIN_FACE_SIZE || h < MIN_FACE_SIZE) {
                    log.debug("跳过人脸框：{}x{} @({}, {})，小于最小尺寸 {}", w, h, x, y, MIN_FACE_SIZE);
                    continue;
                }
                BufferedImage subImage = image.getSubimage(x, y, w, h);
                Image img = bufferedImage2DJLImage(subImage);
                //获取特征向量
                List<Float> feature = faceFeature(img);
                if (feature == null) {
                    log.debug("人脸特征为空，跳过：{}x{} @({}, {})", w, h, x, y);
                    continue;
                }
                FaceObject faceObject = new FaceObject();
                faceObject.setFeature(feature);
                faceObject.setBoundingBox(new Rectangle(x, y, w, h));
                faceObjects.add(faceObject);
            }
            return faceObjects;
        }
    }

    public static List<Float> faceFeature(Image img) throws TranslateException, ModelNotFoundException, MalformedModelException, IOException, OrtException {
        FaceFeatureDetect faceFeatureDetect = new FaceFeatureDetect();
        Criteria<Image, float[]> criteria = faceFeatureDetect.criteria();
        ZooModel<Image, float[]> model = loadModel(faceFeatureDetect.modelName, criteria);
        try (Predictor<Image, float[]> predictor = model.newPredictor(faceFeatureDetect.translator())) {
            float[] embeddings = predictor.predict(img);
            List<Float> feature = new ArrayList<>();
            if (embeddings != null) {
                for (int i = 0; i < embeddings.length; i++) {
                    feature.add(embeddings[i]);
                }
            } else {
                return null;
            }
            return feature;
        }
    }
    public static Image bufferedImage2DJLImage(BufferedImage img) {
        return ImageFactory.getInstance().fromImage(img);
    }

    /**
     * 取缓存中的模型，首次调用才真正加载。
     *
     * <p>{@link DjlModelUtil#getModel} 抛出的 {@link ModelException} 比本类签名更宽，
     * 这里按类型还原，其余情况包装成 IOException，保持对外异常契约不变。
     */
    private static <T> ZooModel<Image, T> loadModel(String key, Criteria<Image, T> criteria)
            throws IOException, ModelNotFoundException, MalformedModelException {
        try {
            return DjlModelUtil.getModel(key, criteria);
        } catch (ModelException e) {
            if (e instanceof ModelNotFoundException) {
                throw (ModelNotFoundException) e;
            }
            if (e instanceof MalformedModelException) {
                throw (MalformedModelException) e;
            }
            throw new IOException("加载模型失败：" + key, e);
        }
    }

}
