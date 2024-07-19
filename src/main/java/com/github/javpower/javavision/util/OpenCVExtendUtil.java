package com.github.javpower.javavision.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * @author skyyan
 */
@Slf4j
public class OpenCVExtendUtil {


    public static void init(){
        synchronized (OpenCVExtendUtil.class){
            // 加载opencv动态库，
            //System.load(ClassLoader.getSystemResource("lib/opencv_java470-无用.dll").getPath());
            log.info("--AbstractDjlTranslator static---  ");
            // 获取操作系统名称
            String osName = System.getProperty("os.name").toLowerCase();
            // 打印操作系统名称
            log.info("Operating System:{}",osName);
            // 判断操作系统类型
            if (osName.contains("win")) {
                nu.pattern.OpenCV.loadLocally();
                log.info(" window 系统.");
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
                log.info("This is a Unix/Linux/Mac operating system.");
                Path path= OpenCVExtendUtil.getPath();
                if(null!=path) {
                    log.info("加载path==="+path);
                    System.load(path.normalize().toString());
                }else{
                    log.error("lib load error!");
                }

            } else {
                log.info("This is another type of operating system.");
            }
        }
    }
    public static Path getPath(){
        try {
            // 获取 OpenCV 类的 Class 对象
            Class<?> openCVClass = Class.forName("nu.pattern.OpenCV");

            // 获取 extractNativeBinary 方法的 Method 对象
            Method extractNativeBinaryMethod = openCVClass.getDeclaredMethod("extractNativeBinary");
            // 设置方法可访问
            extractNativeBinaryMethod.setAccessible(true);
            // 调用 extractNativeBinary 方法并获取结果
            Path binaryPath = (Path) extractNativeBinaryMethod.invoke(null);  // 调用静态方法，传递 null 作为实例
            log.info("OpenCVUtil 路径为: " + binaryPath);
            return  binaryPath;
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
