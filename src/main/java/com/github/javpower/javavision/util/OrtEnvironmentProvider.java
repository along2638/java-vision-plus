package com.github.javpower.javavision.util;

import ai.djl.engine.Engine;
import ai.onnxruntime.OrtEnvironment;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 进程级唯一的 ONNX Runtime 环境。
 *
 * <p>背景：{@link OrtEnvironment} 在 JVM 内是单例，且创建时会记录日志级别、logId 与线程池配置，
 * 后续再以不同参数调用会抛
 * {@code IllegalStateException: Tried to specify the thread pool when creating an OrtEnvironment, but one already exists}。</p>
 *
 * <p>项目里存在两条创建路径，参数并不相同：</p>
 * <ul>
 *   <li>DJL 的 {@code OrtEngine}：{@code getEnvironment(WARNING, "ort-java", threadingOptions)}</li>
 *   <li>YOLOv8 直接调用：{@code getEnvironment()}，内部 logId 为 {@code "java"}</li>
 * </ul>
 *
 * <p>因此先调 {@code /yolo8} 再调 {@code /word/json}（或反过来）必然失败。
 * 这里统一以 DJL 引擎持有的实例为准，两边共用同一份 {@link OrtEnvironment}，从根本上消除冲突。</p>
 *
 * @author gc.x
 */
@Slf4j
public final class OrtEnvironmentProvider {

    private static volatile OrtEnvironment environment;

    private OrtEnvironmentProvider() {
    }

    /**
     * 获取全局唯一的 OrtEnvironment。
     *
     * <p>首次调用会触发 DJL OnnxRuntime 引擎初始化，并取走它内部创建的实例；
     * 之后无论是 DJL 的模型加载还是 YOLOv8 的 session 创建，用的都是同一份环境。</p>
     */
    public static OrtEnvironment get() {
        OrtEnvironment local = environment;
        if (local != null) {
            return local;
        }
        synchronized (OrtEnvironmentProvider.class) {
            if (environment == null) {
                environment = resolve();
            }
            return environment;
        }
    }

    private static OrtEnvironment resolve() {
        try {
            Engine engine = Engine.getEngine("OnnxRuntime");
            // getEnv() 是 OrtEngine 的包级私有方法，只能通过反射访问
            Method getEnv = engine.getClass().getDeclaredMethod("getEnv");
            getEnv.setAccessible(true);
            OrtEnvironment env = (OrtEnvironment) getEnv.invoke(engine);
            if (env != null) {
                log.info("复用 DJL OnnxRuntime 引擎持有的 OrtEnvironment 实例");
                return env;
            }
        } catch (Exception e) {
            log.warn("无法复用 DJL OnnxRuntime 引擎的 OrtEnvironment，回退到默认实例：{}", e.getMessage());
        }
        return OrtEnvironment.getEnvironment();
    }
}
