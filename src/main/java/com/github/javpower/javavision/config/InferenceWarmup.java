package com.github.javpower.javavision.config;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.github.javpower.javavision.detect.ConstructionDetect;
import com.github.javpower.javavision.detect.FaceDetect;
import com.github.javpower.javavision.detect.FaceFeatureDetect;
import com.github.javpower.javavision.detect.FireSmokeDetect;
import com.github.javpower.javavision.detect.OcrV4Detect;
import com.github.javpower.javavision.detect.OcrV4Recognition;
import com.github.javpower.javavision.detect.ReflectiveVestDetect;
import com.github.javpower.javavision.detect.translator.AbstractDjlTranslator;
import com.github.javpower.javavision.util.DjlModelUtil;
import com.github.javpower.javavision.util.InferenceDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动预热：服务对外可用后，在后台把全部 DJL 模型加载进缓存，并对每个模型跑一次空推理。
 *
 * <p>只加载模型是不够的。实测（本机 20 核 CPU，PP-OCRv4）预热后头 10 个请求仍在 2~7 秒，
 * 第 11 个之后才稳定到 0.64 秒——这段时间花在 JIT 编译、算子首次初始化、OpenCV 路径首次调用上，
 * 和"模型是否已加载"无关。所以这里在加载完模型后，再用一张自造的测试图跑一次完整
 * {@code Predictor.predict}，把这条路径也走热，让第一个真实请求就落在稳态耗时上。
 *
 * <p>两个开关（{@code application.yml} 的 {@code vision.inference.warmup} 下）：
 * <ul>
 *   <li>{@code enabled}：默认 true，设为 false 则保持"首次请求才加载"的旧行为；</li>
 *   <li>{@code async}：默认 true 在独立线程里预热，启动过程不被拖慢；设为 false 则阻塞到全部完成
 *       （适合要求"端口能访问时模型一定就绪"的场景，代价是启动时间变长）。</li>
 * </ul>
 *
 * <p>预热失败不会影响启动：加载失败记 warn 并留给首次请求重试；推理预热失败只记 debug，
 * 因为模型本身是好的，只是没能提前走热。
 */
@Slf4j
@Component
public class InferenceWarmup {

    /** 检测类模型的预热图尺寸。 */
    private static final int DETECT_SIZE = 640;

    /** OCR 识别模型的预热图尺寸（识别网络固定高 48）。 */
    private static final int REC_WIDTH = 320;

    private static final int REC_HEIGHT = 48;

    /** 人脸特征模型的预热图尺寸。 */
    private static final int FACE_SIZE = 112;

    /** 需要预热的模型，顺序即加载顺序。 */
    private static final Map<String, ModelSpec> MODELS = buildModels();

    @Value("${vision.inference.warmup.enabled:true}")
    private boolean enabled;

    @Value("${vision.inference.warmup.async:true}")
    private boolean async;

    /** 是否连推理一起预热，关掉可只做模型加载（加载耗时是主要部分，但走路不用走热）。 */
    @Value("${vision.inference.warmup.predict:true}")
    private boolean predict;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile String state = "未开始";

    private volatile int loaded = 0;

    private volatile int failed = 0;

    private volatile long costMs = -1L;

    /**
     * 等 Spring 容器完全就绪（端口已监听）后再预热，避免拖慢启动与健康检查的上线时间。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            state = "已关闭";
            log.info("模型预热已关闭（vision.inference.warmup.enabled=false），模型将在首次请求时加载");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (async) {
            // 一次性任务，直接起守护线程即可，没必要为它单独建线程池
            Thread thread = new Thread(this::warmUpQuietly, "model-warmup");
            thread.setDaemon(true);
            thread.start();
        } else {
            warmUp();
        }
    }

    /**
     * 预热状态，供 {@code /system/status} 展示。
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", enabled);
        map.put("async", async);
        map.put("predict", predict);
        map.put("state", state);
        map.put("loaded", loaded);
        map.put("failed", failed);
        map.put("costMs", costMs);
        return map;
    }

    /**
     * 后台预热入口：兜住所有 Throwable，避免线程带着异常静默退出、状态一直停在"预热中"。
     */
    private void warmUpQuietly() {
        try {
            warmUp();
        } catch (Throwable e) {
            state = "失败";
            log.error("模型预热线程异常退出", e);
        }
    }

    private void warmUp() {
        state = "预热中";
        long start = System.currentTimeMillis();
        log.info("模型预热开始（设备={}，共 {} 个模型，{}），预热期间服务可正常接收请求",
                InferenceDevice.device(), MODELS.size(), predict ? "含推理预热" : "仅加载模型");
        int ok = 0;
        int bad = 0;
        for (Map.Entry<String, ModelSpec> entry : MODELS.entrySet()) {
            String name = entry.getKey();
            ModelSpec spec = entry.getValue();
            long itemStart = System.currentTimeMillis();
            try {
                AbstractDjlTranslator<?> translator = spec.factory.create();
                loadAndPredict(translator, spec);
                ok++;
                log.info("  [预热] {} 完成，耗时 {}ms", name, System.currentTimeMillis() - itemStart);
            } catch (Throwable e) {
                // 用 Throwable 兜底：缺原生库时抛的是 UnsatisfiedLinkError，不能让它中断后续模型
                bad++;
                log.warn("  [预热] {} 失败，将在首次请求时重试：{}", name, e.toString());
            }
        }
        costMs = System.currentTimeMillis() - start;
        loaded = ok;
        failed = bad;
        state = bad == 0 ? "完成" : "部分失败";
        log.info("模型预热{}：成功 {} 个，失败 {} 个，总耗时 {}ms",
                bad == 0 ? "完成" : "结束（有失败项）", ok, bad, costMs);
    }

    /**
     * 加载并（可选）走一次推理。
     *
     * <p>加载失败直接抛出，由调用方计入 {@code failed}；推理失败只降级记日志——
     * 模型本身已就绪，业务请求能用，只是没吃到"走热"的收益。
     */
    private <T> void loadAndPredict(AbstractDjlTranslator<T> translator, ModelSpec spec) throws Exception {
        Criteria<Image, T> criteria = translator.criteria();
        ZooModel<Image, T> model = DjlModelUtil.getModel(translator.modelName, criteria);
        if (!predict) {
            return;
        }
        try (Predictor<Image, T> predictor = model.newPredictor(translator.translator())) {
            predictor.predict(dummyImage(spec.width, spec.height));
        } catch (Throwable e) {
            log.debug("  [预热] {} 推理预热失败（模型已就绪，不影响使用）：{}",
                    translator.modelName, e.toString());
        }
    }

    /**
     * 造一张测试图：白底 + 若干黑色横条。
     *
     * <p>不追求识别出内容，只要求输入非全空，避免各 translator 走到"零结果"的短路分支，
     * 从而真正执行到前处理与后处理的全部代码。
     */
    private static Image dummyImage(int width, int height) {
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = buffered.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.DARK_GRAY);
            int lineHeight = Math.max(4, height / 16);
            int gap = Math.max(lineHeight + 4, height / 6);
            for (int i = 0; i < 5; i++) {
                int y = Math.max(2, height / 10 + i * gap);
                if (y + lineHeight >= height) {
                    break;
                }
                graphics.fillRect(Math.max(2, width / 10), y, Math.max(8, width * 4 / 5), lineHeight);
            }
        } finally {
            graphics.dispose();
        }
        return ImageFactory.getInstance().fromImage(buffered);
    }

    private static Map<String, ModelSpec> buildModels() {
        Map<String, ModelSpec> models = new LinkedHashMap<>();
        models.put("人脸检测 ultranet.zip",
                new ModelSpec(() -> new FaceDetect(faceArgs()), DETECT_SIZE, DETECT_SIZE));
        models.put("人脸特征 face_feature.zip",
                new ModelSpec(FaceFeatureDetect::new, FACE_SIZE, FACE_SIZE));
        models.put("OCR文字检测 ch_PP-OCRv4_det_infer.onnx",
                new ModelSpec(() -> new OcrV4Detect(new HashMap<>()), DETECT_SIZE, DETECT_SIZE));
        models.put("OCR文字识别 ch_PP-OCRv4_rec_infer.onnx",
                new ModelSpec(() -> new OcrV4Recognition(new HashMap<>()), REC_WIDTH, REC_HEIGHT));
        models.put("烟火检测 fire_smoke.zip",
                new ModelSpec(() -> new FireSmokeDetect(detectArgs()), DETECT_SIZE, DETECT_SIZE));
        models.put("安全帽检测 helmet_head_person_s.zip",
                new ModelSpec(() -> new ConstructionDetect(detectArgs()), DETECT_SIZE, DETECT_SIZE));
        models.put("反光衣检测 reflective_clothes.zip",
                new ModelSpec(() -> new ReflectiveVestDetect(detectArgs()), DETECT_SIZE, DETECT_SIZE));
        return models;
    }

    /**
     * 人脸检测参数，与 {@code FaceDetectUtil#faceDetect} 保持一致。
     */
    private static Map<String, Object> faceArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("topK", 200);
        args.put("confThresh", 0.85);
        args.put("nmsThresh", 0.45);
        return args;
    }

    /**
     * YOLO 类检测参数，与各 {@code *DetectUtil} 保持一致（尺寸只影响 translator，不影响模型本身）。
     */
    private static Map<String, Object> detectArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("width", DETECT_SIZE);
        args.put("height", DETECT_SIZE);
        args.put("resize", true);
        args.put("rescale", true);
        args.put("threshold", 0.2);
        args.put("nmsThreshold", 0.5);
        return args;
    }

    /**
     * 模型构造工厂。
     *
     * <p>不用 {@code java.util.function.Supplier} 是因为部分构造器会抛受检异常
     * （如 {@code FaceFeatureDetect()} 抛 {@code OrtException}），
     * Supplier 不允许，会让 {@code FaceFeatureDetect::new} 无法直接引用。
     */
    @FunctionalInterface
    private interface ModelFactory {
        AbstractDjlTranslator<?> create() throws Exception;
    }

    /** 一个待预热模型：怎么造 translator + 预热图多大。 */
    private static final class ModelSpec {
        private final ModelFactory factory;
        private final int width;
        private final int height;

        private ModelSpec(ModelFactory factory, int width, int height) {
            this.factory = factory;
            this.width = width;
            this.height = height;
        }
    }
}
