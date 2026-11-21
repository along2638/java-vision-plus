package com.github.javpower.javavision.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 推理线程数配置。
 *
 * <p>libtorch 与 onnxruntime 默认都把 intra-op 线程数设成 CPU 逻辑核数：
 * 20 核机器上单个请求会拉起 20 个计算线程。机器只跑一个请求时这样最快，
 * 但作为 Web 服务并发进来时，6 个请求就是 120 个线程抢 20 个核，上下文切换和
 * 缓存抖动会把单请求耗时抬到单线程的好几倍（实测 6 并发 9s）。
 *
 * <p>两个引擎都支持用系统属性在初始化前定死线程数，因此这里只需要在
 * <b>任何引擎初始化之前</b>把属性写进去：
 * <ul>
 *   <li>{@code ai.djl.pytorch.num_threads} / {@code ai.djl.pytorch.num_interop_threads}</li>
 *   <li>{@code ai.djl.onnxruntime.num_threads} / {@code ai.djl.onnxruntime.num_interop_threads}</li>
 * </ul>
 * 一旦引擎加载完成，这些属性就不再起作用，所以必须在 {@code main} 的最前面落地。
 *
 * @author javavision
 */
@Slf4j
public final class InferenceThreads {

    /** 配置项名，支持 -D 参数、环境变量、Spring 命令行参数三种写法。 */
    public static final String CONFIG_KEY = "vision.inference.threads";

    /** 0 表示不干预，沿用引擎默认（等于 CPU 逻辑核数）。 */
    public static final int DEFAULT_THREADS = 0;

    private static final String PYTORCH_THREADS = "ai.djl.pytorch.num_threads";

    private static final String PYTORCH_INTEROP = "ai.djl.pytorch.num_interop_threads";

    private static final String ORT_THREADS = "ai.djl.onnxruntime.num_threads";

    private static final String ORT_INTEROP = "ai.djl.onnxruntime.num_interop_threads";

    private static volatile int threads = DEFAULT_THREADS;

    private static volatile boolean applied = false;

    private InferenceThreads() {
    }

    /**
     * 解析配置并写入引擎系统属性，必须在任何 DJL 推理代码之前调用。
     *
     * <p>优先级：{@code -Dvision.inference.threads} &gt; 环境变量 {@code INFERENCE_THREADS}
     * &gt; {@code --vision.inference.threads=N} &gt; 不干预。
     *
     * @param args Spring Boot 启动参数
     */
    public static synchronized void initFromArgs(String[] args) {
        if (applied) {
            return;
        }
        applied = true;

        int value = resolve(args);
        if (value <= 0) {
            threads = DEFAULT_THREADS;
            log.info("推理线程数：不干预（引擎默认 = CPU 逻辑核数 {}）", Runtime.getRuntime().availableProcessors());
            return;
        }

        threads = value;
        // 主线程数与 inter-op 线程数分开：模型内部算子按 intra-op 并行，
        // inter-op 只在多算子可并行时才有意义，固定 1 避免额外线程。
        System.setProperty(PYTORCH_THREADS, String.valueOf(value));
        System.setProperty(PYTORCH_INTEROP, "1");
        System.setProperty(ORT_THREADS, String.valueOf(value));
        System.setProperty(ORT_INTEROP, "1");
        log.info("推理线程数：已限制为 {}（PyTorch 与 onnxruntime 的 intra-op 均为 {}，inter-op 为 1）",
                value, value);
    }

    /** 当前生效的线程数，0 表示未干预。 */
    public static int current() {
        return threads;
    }

    private static int resolve(String[] args) {
        String raw = System.getProperty(CONFIG_KEY);
        if (isBlank(raw)) {
            raw = System.getenv("INFERENCE_THREADS");
        }
        if (isBlank(raw) && args != null) {
            String prefix = "--" + CONFIG_KEY + "=";
            for (String arg : args) {
                if (arg != null && arg.startsWith(prefix)) {
                    raw = arg.substring(prefix.length());
                    break;
                }
            }
        }
        if (isBlank(raw)) {
            return DEFAULT_THREADS;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                log.warn("{} 不能为负数（{}），按不干预处理", CONFIG_KEY, value);
                return DEFAULT_THREADS;
            }
            return value;
        } catch (NumberFormatException e) {
            log.warn("无法解析 {} 取值 [{}]，按不干预处理", CONFIG_KEY, raw);
            return DEFAULT_THREADS;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
