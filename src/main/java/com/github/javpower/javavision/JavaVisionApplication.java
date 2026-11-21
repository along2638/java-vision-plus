package com.github.javpower.javavision;

import com.github.javpower.javavision.util.DjlModelUtil;
import com.github.javpower.javavision.util.InferenceDevice;
import com.github.javpower.javavision.util.InferenceThreads;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@MapperScan("com.github.javpower.javavision.db.mapper")
@SpringBootApplication
public class JavaVisionApplication {

    /**
     * 未显式配置端口时的兜底值，仅用于启动日志提示
     */
    private static final String DEFAULT_PORT = "6888";

    public static void main(String[] args) {
        // 推理设备必须在任何 DJL 引擎初始化之前定稿：CPU / CUDA 两套 libtorch 同名，
        // 一个 JVM 只能加载一套，加载后无法更换。
        // 决策依据 = 产物 flavor（mvn package -Dgpu=true 生成的标记）∩ 显卡探测结果。
        // 可用 -Dvision.inference.device=cpu|cuda|auto 强制指定。
        InferenceDevice.initFromArgs(args);
        log.info("推理设备: {}", InferenceDevice.summary());

        // 线程数同样必须在引擎初始化前定稿：两个引擎都只读取一次系统属性
        InferenceThreads.initFromArgs(args);

        Runtime.getRuntime().addShutdownHook(new Thread(DjlModelUtil::clear, "djl-model-release"));
        ConfigurableApplicationContext context = SpringApplication.run(JavaVisionApplication.class, args);
        log.info("接口文档: http://localhost:{}/doc.html",
                context.getEnvironment().getProperty("local.server.port", DEFAULT_PORT));
    }
}
