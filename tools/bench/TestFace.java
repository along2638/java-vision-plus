import ai.djl.modality.cv.Image;
import com.github.javpower.javavision.entity.FaceObject;
import com.github.javpower.javavision.util.FaceDetectUtil;
import com.github.javpower.javavision.util.FaceSimilarityCalculator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 人脸链路验证：
 * <pre>
 *   java TestFace 图片              正常检测 + 特征提取
 *   java TestFace 图片 tiny         裁 2×2 送 facenet（崩溃现场的形状）
 *   java TestFace 图片 sim          同一张脸按不同尺寸裁剪，比较特征余弦相似度
 * </pre>
 */
public class TestFace {
    public static void main(String[] args) throws Exception {
        String path = args[0];
        String mode = args.length > 1 ? args[1] : "detect";

        if ("tiny".equals(mode)) {
            BufferedImage src = ImageIO.read(new File(path));
            Image img = FaceDetectUtil.bufferedImage2DJLImage(src.getSubimage(0, 0, 2, 2));
            long t0 = System.currentTimeMillis();
            try {
                List<Float> f = FaceDetectUtil.faceFeature(img);
                System.out.printf("[OK 2x2] 特征维度=%d，耗时 %d ms%n",
                        f == null ? -1 : f.size(), System.currentTimeMillis() - t0);
            } catch (Throwable e) {
                System.out.printf("[FAIL 2x2] %s: %s%n", e.getClass().getName(), e.getMessage());
            }
            return;
        }

        if ("sim".equals(mode)) {
            // 用检测结果里第一张清晰人脸（4624×3472 原图坐标）
            int bx = 3413, by = 960, bw = 365, bh = 380;
            BufferedImage src = ImageIO.read(new File(path));
            List<List<Float>> feats = new ArrayList<>();
            for (double s : new double[]{1.0, 0.5, 0.25, 0.1}) {
                int w = Math.max(1, (int) (bw * s));
                int h = Math.max(1, (int) (bh * s));
                try {
                    BufferedImage sub = src.getSubimage(bx, by, w, h);
                    List<Float> f = FaceDetectUtil.faceFeature(FaceDetectUtil.bufferedImage2DJLImage(sub));
                    feats.add(f);
                    System.out.printf("裁剪 %dx%d -> 特征维度=%d%n", w, h, f == null ? -1 : f.size());
                } catch (Throwable e) {
                    feats.add(null);
                    System.out.printf("裁剪 %dx%d -> 失败: %s%n", w, h, e.getMessage());
                }
            }
            for (int i = 1; i < feats.size(); i++) {
                if (feats.get(0) == null || feats.get(i) == null) {
                    System.out.printf("基准(365x380) vs 缩放后 #%d -> 无法比较（有失败）%n", i);
                    continue;
                }
                double sim = FaceSimilarityCalculator.calculateCosineSimilarity(
                        feats.get(0).toArray(new Float[0]), feats.get(i).toArray(new Float[0]));
                System.out.printf("基准(365x380) vs 缩放后 #%d -> 余弦相似度 %.6f%n", i, sim);
            }
            return;
        }

        long t0 = System.currentTimeMillis();
        try {
            List<FaceObject> list = FaceDetectUtil.faceDetect(path);
            System.out.printf("[OK] %s -> 人脸 %d 个，耗时 %d ms%n",
                    path, list.size(), System.currentTimeMillis() - t0);
            for (int i = 0; i < list.size(); i++) {
                FaceObject f = list.get(i);
                System.out.printf("     #%d 特征维度=%s 框=%s%n", i,
                        f.getFeature() == null ? "null" : String.valueOf(f.getFeature().size()),
                        f.getBoundingBox());
            }
        } catch (Throwable e) {
            System.out.printf("[FAIL] %s -> %s: %s%n", path, e.getClass().getName(), e.getMessage());
        }
    }
}
