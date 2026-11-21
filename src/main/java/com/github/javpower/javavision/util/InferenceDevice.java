package com.github.javpower.javavision.util;

import ai.djl.Device;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动期推理设备决策。
 *
 * <p>libtorch 的 CPU 版与 CUDA 版是两套同名的动态库（{@code torch.dll} / {@code torch_cuda.dll}），
 * 同一个 JVM 只能加载其中一套，且一旦 {@code Engine} 初始化完成就无法更换。
 * 因此"优先 GPU、没有就回落 CPU"只能在启动的第一时间定下来，不能按请求动态切。
 *
 * <p>决策依据是两个条件的交集：
 * <ol>
 *   <li><b>产物能力</b>：构建时写入 {@code inference-flavor.properties} 的 flavor，cpu 或 cu121。
 *       由 Maven 的 {@code -Dgpu=true} 切换，CPU 版产物无论机器多强都不可能走 GPU。</li>
 *   <li><b>运行硬件</b>：{@code nvidia-smi} 能否列出显卡，以及驱动支持的 CUDA 版本是否 ≥ 12.1。</li>
 * </ol>
 *
 * <p>{@code PYTORCH_FLAVOR} 始终等于产物 flavor 而不是最终设备：GPU 版产物在无卡机器上回落时，
 * 若把 flavor 改成 cpu，DJL 会去 djl.ai 重新下载 CPU 原生库（离线环境直接失败），
 * 而 cu121 版的 libtorch 本身也包含完整的 CPU kernel，可以直接用来做 CPU 推理。
 *
 * @author javavision
 */
@Slf4j
public final class InferenceDevice {

    /** 构建期写入 classpath 的产物标记文件。 */
    private static final String FLAVOR_RESOURCE = "/inference-flavor.properties";

    /** 配置项名，支持 -D 参数、环境变量、Spring 命令行参数三种写法。 */
    public static final String CONFIG_KEY = "vision.inference.device";

    /** cu121 版 libtorch 要求的最低驱动 CUDA 版本。 */
    private static final double MIN_CUDA_VERSION = 12.1d;

    /**
     * cu121 版 libtorch（torch 2.1.1）编译覆盖的算力区间。
     *
     * <p>官方构建的 {@code TORCH_CUDA_ARCH_LIST} 是 {@code 5.0;6.0;7.0;7.5;8.0;8.6;9.0}，
     * 所以 Maxwell(sm_50) 到 Hopper(sm_90) 都有 kernel 镜像；
     * Blackwell(sm_100+) 没有，运行时会报 "no kernel image is available"。
     */
    private static final double MIN_COMPUTE_CAPABILITY = 5.0d;

    private static final double MAX_COMPUTE_CAPABILITY = 9.0d;

    /** 显卡探测超时，避免在没有驱动的机器上卡住启动。 */
    private static final long PROBE_TIMEOUT_SECONDS = 5L;

    private static final Pattern CUDA_VERSION_PATTERN = Pattern.compile("CUDA Version:\\s*([0-9]+\\.[0-9]+)");

    private static volatile boolean initialized = false;

    private static volatile Device device = Device.cpu();

    private static volatile String flavor = "cpu";

    private static volatile boolean gpuActive = false;

    private static volatile String summary = "尚未初始化";

    /** 探测到的显卡信息，未检测到时为 null，仅用于日志与状态接口展示。 */
    private static volatile GpuInfo probedGpu;

    private InferenceDevice() {
    }

    /**
     * 执行一次设备决策，必须在任何 DJL 推理代码之前调用。
     *
     * <p>重复调用是幂等的，只有第一次生效。
     *
     * @param prefer 期望的设备，取值 {@code auto} / {@code cpu} / {@code cuda}，为空按 auto 处理
     */
    public static synchronized void init(String prefer) {
        if (initialized) {
            return;
        }
        initialized = true;

        flavor = readFlavor();
        boolean packageHasCuda = "cu121".equals(flavor);

        // 产物决定 flavor，必须最先设置：DJL 的 Engine 初始化时会读取它
        System.setProperty("PYTORCH_FLAVOR", flavor);

        GpuInfo gpu = probeGpu();
        probedGpu = gpu;
        Mode mode = Mode.parse(prefer);

        boolean useGpu = false;
        String reason;

        if (mode == Mode.CPU) {
            reason = "配置显式指定 cpu";
        } else if (!packageHasCuda) {
            if (mode == Mode.CUDA) {
                throw new IllegalStateException(
                        "配置要求使用 GPU，但当前产物是 CPU 版原生库（flavor=cpu）。"
                                + "请使用 mvn package -Dgpu=true 重新打包后再启动。");
            }
            reason = "当前产物为 CPU 版原生库（flavor=cpu），不具备 CUDA 能力";
        } else if (gpu == null) {
            if (mode == Mode.CUDA) {
                throw new IllegalStateException(
                        "配置要求使用 GPU，但未检测到可用的 NVIDIA 显卡（nvidia-smi 无输出）。"
                                + "请检查驱动安装，或将 " + CONFIG_KEY + " 设为 auto/cpu。");
            }
            reason = "未检测到可用的 NVIDIA 显卡";
        } else if (gpu.cudaVersion < MIN_CUDA_VERSION) {
            if (mode == Mode.CUDA) {
                throw new IllegalStateException(
                        "配置要求使用 GPU，但驱动仅支持 CUDA " + gpu.cudaVersion
                                + "，cu121 版 libtorch 要求不低于 " + MIN_CUDA_VERSION
                                + "。请升级 NVIDIA 驱动。");
            }
            reason = "驱动 CUDA 版本过低（" + gpu.cudaVersion + " < " + MIN_CUDA_VERSION + "）";
        } else if (!isArchitectureSupported(gpu)) {
            String detail = "显卡 " + gpu.name + " 属于 " + gpu.architecture()
                    + "（算力 " + gpu.computeCapability + "），"
                    + "而 cu121 版 libtorch 只编译到 sm_90，没有该架构的 kernel 镜像";
            if (mode == Mode.CUDA) {
                throw new IllegalStateException("配置要求使用 GPU，但 " + detail
                        + "。Blackwell 需要 PyTorch 2.7+ 配 cu128 及以上版本，"
                        + "请升级 DJL 与 PyTorch 原生库，或改用 Ada/Ampere/Hopper 显卡。");
            }
            log.warn("{},已回落 CPU。若该卡为部署目标,需升级 PyTorch 版本后重新打包", detail);
            reason = detail + "，回落 CPU";
        } else {
            useGpu = true;
            reason = "检测到 " + gpu.name + "（" + gpu.architecture()
                    + " 算力 " + gpu.computeCapability + "，驱动支持 CUDA " + gpu.cudaVersion + "）";
        }

        gpuActive = useGpu;
        device = useGpu ? Device.gpu() : Device.cpu();

        summary = String.format("产物=%s, 期望=%s, 结果=%s, 原因=%s",
                flavor, mode.name().toLowerCase(), useGpu ? "GPU" : "CPU", reason);

        printDecision(gpu, mode, useGpu, reason);
    }

    /**
     * 按系统属性 / 环境变量 / 命令行参数解析期望设备，然后初始化。
     *
     * <p>优先级：{@code -Dvision.inference.device} &gt; 环境变量 {@code INFERENCE_DEVICE} &gt; auto
     *
     * @param args Spring Boot 启动参数，用于识别 {@code --vision.inference.device=xxx}
     */
    public static synchronized void initFromArgs(String[] args) {
        String prefer = System.getProperty(CONFIG_KEY);
        if (isBlank(prefer)) {
            prefer = System.getenv("INFERENCE_DEVICE");
        }
        if (isBlank(prefer) && args != null) {
            String prefix = "--" + CONFIG_KEY + "=";
            for (String arg : args) {
                if (arg != null && arg.startsWith(prefix)) {
                    prefer = arg.substring(prefix.length());
                    break;
                }
            }
        }
        init(prefer);
    }

    /** 当前推理设备，DJL 的 {@code Criteria.optDevice} 使用。 */
    public static Device device() {
        // 兜底：未经启动类初始化的场景（测试、单独调用工具类）按 auto 现场判定一次
        if (!initialized) {
            init("auto");
        }
        return device;
    }

    /** 是否真正启用了 GPU。 */
    public static boolean isGpuActive() {
        return gpuActive;
    }

    /** 产物自带的 flavor（cpu / cu121），与最终设备无关。 */
    public static String flavor() {
        return flavor;
    }

    /** 决策摘要，用于启动日志与状态接口。 */
    public static String summary() {
        return summary;
    }

    /** 供 {@code /system/status} 输出的结构化信息。 */
    public static Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("device", gpuActive ? "GPU" : "CPU");
        map.put("flavor", flavor);
        map.put("gpuActive", gpuActive);
        GpuInfo gpu = probedGpu;
        if (gpu != null) {
            map.put("gpuName", gpu.name);
            map.put("computeCapability", gpu.computeCapability > 0
                    ? String.valueOf(gpu.computeCapability) : "未知");
            map.put("gpuArchitecture", gpu.architecture());
        }
        map.put("summary", summary);
        return map;
    }

    private static void printDecision(GpuInfo gpu, Mode mode, boolean useGpu, String reason) {
        String icon = useGpu ? "GPU" : "CPU";
        log.info("");
        log.info("==================== 推理设备决策 ====================");
        log.info("  产物 flavor : {}", flavor);
        log.info("  期望模式    : {}", mode.name().toLowerCase());
        log.info("  显卡        : {}", gpu == null ? "未检测到"
                : gpu.name + " (" + gpu.architecture() + ", 算力 " + gpu.computeCapability
                        + ", 驱动 CUDA " + gpu.cudaVersion + ")");
        log.info("  最终结果    : {}", icon);
        log.info("  判定原因    : {}", reason);
        log.info("======================================================");
        log.info("");
        if (useGpu) {
            log.info("已启用 GPU 推理，模型将在首次加载时占用显存");
        } else if ("cu121".equals(flavor)) {
            log.info("当前为 GPU 版产物，但本次以 CPU 方式运行（libtorch 的 CUDA 版同样包含 CPU kernel）");
        }
    }

    /**
     * 读取构建期写入的 flavor 标记。
     *
     * <p>IDE 中直接运行（未经过 Maven 资源过滤）时文件内容会是未替换的占位符，
     * 这种情况按 cpu 处理，避免开发环境误判。
     */
    private static String readFlavor() {
        try (InputStream in = InferenceDevice.class.getResourceAsStream(FLAVOR_RESOURCE)) {
            if (in == null) {
                log.warn("未找到 {}，按 CPU 产物处理", FLAVOR_RESOURCE);
                return "cpu";
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            String value = properties.getProperty("flavor", "cpu").trim();
            // 未经 Maven 资源过滤时会是占位符原样（@inference.flavor@ 或 ${inference.flavor}）
            if (value.isEmpty() || value.contains("@") || value.contains("${")) {
                log.warn("{} 未经 Maven 资源过滤（flavor={}），按 CPU 产物处理", FLAVOR_RESOURCE, value);
                return "cpu";
            }
            return value;
        } catch (Exception e) {
            log.warn("读取产物 flavor 失败，按 CPU 产物处理", e);
            return "cpu";
        }
    }

    /**
     * 探测可用的 NVIDIA 显卡。
     *
     * <p>型号走 {@code --query-gpu=name} 直接取，避免解析 nvidia-smi 的表格布局
     * （表头行里的 Fan / Temp 会被误认成型号）；CUDA 版本只能从完整输出的表头解析。
     *
     * @return 探测结果，未检测到返回 null
     */
    private static GpuInfo probeGpu() {
        for (String exe : nvidiaSmiCandidates()) {
            List<String> nameLines = exec(exe, "--query-gpu=name,compute_cap", "--format=csv,noheader");
            double computeCapability = 0d;
            if (nameLines != null && !nameLines.isEmpty()) {
                String[] parts = nameLines.get(0).split(",", 2);
                if (parts.length > 1) {
                    computeCapability = parseComputeCapability(parts[1].trim());
                }
            } else {
                // 老驱动没有 compute_cap 字段，退化为只查型号
                nameLines = exec(exe, "--query-gpu=name", "--format=csv,noheader");
            }
            if (nameLines == null || nameLines.isEmpty()) {
                continue;
            }
            String name = nameLines.get(0).split(",", 2)[0].trim();
            if (name.isEmpty() || name.contains("ERROR")) {
                continue;
            }
            List<String> detail = exec(exe);
            if (detail == null) {
                continue;
            }
            Matcher matcher = CUDA_VERSION_PATTERN.matcher(String.join("\n", detail));
            if (!matcher.find()) {
                continue;
            }
            double cudaVersion;
            try {
                cudaVersion = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            return new GpuInfo(name, cudaVersion, computeCapability);
        }
        return null;
    }

    private static List<String> nvidiaSmiCandidates() {
        List<String> candidates = new ArrayList<>();
        candidates.add("nvidia-smi");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            candidates.add("C:\\Windows\\System32\\nvidia-smi.exe");
            candidates.add("C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe");
        } else {
            candidates.add("/usr/bin/nvidia-smi");
            candidates.add("/usr/local/nvidia/bin/nvidia-smi");
        }
        return candidates;
    }

    private static List<String> exec(String... command) {
        if (command == null || command.length == 0) {
            return null;
        }
        Process process = null;
        try {
            File file = new File(command[0]);
            if (file.isAbsolute() && !file.exists()) {
                return null;
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.debug("执行 {} 超时", command[0]);
                return null;
            }
            return process.exitValue() == 0 ? lines : null;
        } catch (Exception e) {
            log.debug("执行 {} 失败: {}", command[0], e.getMessage());
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 解析 nvidia-smi 输出的算力值，如 {@code 6.1}。
     *
     * @param value 原始字符串，老驱动或虚拟化环境下可能是 {@code [N/A]}
     * @return 解析结果，无法解析返回 0（表示未知，不参与架构判断）
     */
    private static double parseComputeCapability(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * 判断显卡算力是否落在 cu121 版 libtorch 的编译区间内。
     *
     * <p>算力未知时按支持处理：老驱动不支持查询该字段，不宜因此拒绝使用 GPU。
     */
    private static boolean isArchitectureSupported(GpuInfo gpu) {
        double capability = gpu.computeCapability;
        if (capability <= 0) {
            return true;
        }
        return capability >= MIN_COMPUTE_CAPABILITY && capability <= MAX_COMPUTE_CAPABILITY;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 期望的设备模式。 */
    private enum Mode {
        AUTO, CPU, CUDA;

        static Mode parse(String value) {
            if (isBlank(value)) {
                return AUTO;
            }
            String normalized = value.trim().toLowerCase();
            if ("cpu".equals(normalized)) {
                return CPU;
            }
            if ("cuda".equals(normalized) || "gpu".equals(normalized)) {
                return CUDA;
            }
            if (!"auto".equals(normalized)) {
                log.warn("无法识别的 {} 取值 [{}]，按 auto 处理", CONFIG_KEY, value);
            }
            return AUTO;
        }
    }

    /** 显卡探测结果。 */
    private static final class GpuInfo {
        private final String name;
        private final double cudaVersion;
        private final double computeCapability;

        private GpuInfo(String name, double cudaVersion, double computeCapability) {
            this.name = name;
            this.cudaVersion = cudaVersion;
            this.computeCapability = computeCapability;
        }

        /** 按算力推断微架构名称，用于日志与状态展示。 */
        private String architecture() {
            double c = computeCapability;
            if (c <= 0) {
                return "架构未知";
            } else if (c >= 10) {
                return "Blackwell";
            } else if (c >= 9) {
                return "Hopper";
            } else if (c >= 8.9) {
                return "Ada Lovelace";
            } else if (c >= 8) {
                return "Ampere";
            } else if (c >= 7.5) {
                return "Turing";
            } else if (c >= 7) {
                return "Volta";
            } else if (c >= 6) {
                return "Pascal";
            } else {
                return "Maxwell";
            }
        }
    }
}
