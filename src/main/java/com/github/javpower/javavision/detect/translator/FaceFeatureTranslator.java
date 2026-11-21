package com.github.javpower.javavision.detect.translator;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * @author gc.x
 * @date 2022-04
 */
public final class FaceFeatureTranslator implements Translator<Image, float[]> {

    /**
     * 是否把输入统一缩放到 160×160。
     *
     * <p>facenet（inception_resnet_v1）按 160×160 训练，正常应保持开启；
     * 关闭后输入尺寸等于检测框像素，小框会让网络下采样到 2×2 并抛
     * "Calculated padded input size per channel: (2 x 2)"。
     * 保留开关用于灰度对比：{@code -Dvision.face.resize=false}。
     */
    private static final boolean RESIZE_TO_160 =
            !"false".equalsIgnoreCase(System.getProperty("vision.face.resize", "true"));

    public FaceFeatureTranslator() {
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Image input) {
        NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);
        Pipeline pipeline = new Pipeline();
        if (RESIZE_TO_160) {
            pipeline.add(new Resize(160, 160));
        }
        pipeline.add(new ToTensor())
                .add(
                        new Normalize(
                                new float[]{127.5f / 255.0f, 127.5f / 255.0f, 127.5f / 255.0f},
                                new float[]{128.0f / 255.0f, 128.0f / 255.0f, 128.0f / 255.0f}));

        return pipeline.transform(new NDList(array));
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        NDList result = new NDList();
        long numOutputs = list.singletonOrThrow().getShape().get(0);
        for (int i = 0; i < numOutputs; i++) {
            result.add(list.singletonOrThrow().get(i));
        }
        float[][] embeddings = result.stream().map(NDArray::toFloatArray).toArray(float[][]::new);
        float[] feature = new float[embeddings.length];
        for (int i = 0; i < embeddings.length; i++) {
            feature[i] = embeddings[i][0];
        }
        return feature;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
}