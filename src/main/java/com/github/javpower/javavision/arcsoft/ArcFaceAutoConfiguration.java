package com.github.javpower.javavision.arcsoft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Auto-configuration for ArcFace library.
 */
@Slf4j
@Configuration
public class ArcFaceAutoConfiguration implements InitializingBean, DisposableBean {

    private static final String PLATFORM;

    private static final String USER_HOME;

    public static String CACHE_LIB_FOLDER;

    private static final String LIBS_DIRECTORY = "libs/";
    private static final String DEFAULT_SUFFIX = ".dll";
    private static final String SO_SUFFIX = ".so";

    @Value("${config.arcface-sdk.version}")
    private String arcFaceVersion;

    static {
        String jvmName = System.getProperty("java.vm.name", "").toLowerCase();
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();
        String abiType = System.getProperty("sun.arch.abi", "").toLowerCase();
        String libPath = System.getProperty("sun.boot.library.path", "").toLowerCase();
        USER_HOME = System.getProperty("user.home");
        if (jvmName.startsWith("dalvik") && osName.startsWith("linux")) {
            osName = "android";
        } else if (jvmName.startsWith("robovm") && osName.startsWith("darwin")) {
            osName = "ios";
            osArch = "arm";
        } else if (osName.startsWith("mac os x") || osName.startsWith("darwin")) {
            osName = "macosx";
        }

        PLATFORM = osName + "-" + normalizeArch(osArch, abiType, libPath);
    }

    private static String normalizeArch(String osArch, String abiType, String libPath) {
        if (osArch.equals("i386") || osArch.equals("i486") || osArch.equals("i586") || osArch.equals("i686")) {
            return "x86";
        } else if (osArch.equals("amd64") || osArch.equals("x86-64") || osArch.equals("x64")) {
            return "x86_64";
        } else if (osArch.startsWith("aarch64") || osArch.startsWith("armv8") || osArch.startsWith("arm64")) {
            return "arm64";
        } else if ((osArch.startsWith("arm")) && ((abiType.equals("gnueabihf")) || (libPath.contains("openjdk-armhf")))) {
            return "armhf";
        } else if (osArch.startsWith("arm")) {
            return "arm";
        }
        return osArch;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        CACHE_LIB_FOLDER = USER_HOME + "/.arcface/cache/" + arcFaceVersion + "/" + PLATFORM + "/";
        loadLibrary();
    }

    private void loadLibrary() throws IOException {
        String baseFolder = determineBaseFolder();
        String suffix = determineSuffix();

        if (baseFolder.isEmpty()) {
            log.error("ArcFace does not support this operating system.");
            return;
        }

        File cacheLibFolder = new File(CACHE_LIB_FOLDER);
        ensureDirectoryExists(cacheLibFolder);

        List<String> libraryNames = Arrays.asList("libarcsoft_face", "libarcsoft_face_engine", "libarcsoft_face_engine_jni");

        for (String lib : libraryNames) {
            String resourcePath = LIBS_DIRECTORY + arcFaceVersion + "/" + baseFolder + "/" + lib + suffix;
            copyLibraryResource(cacheLibFolder, lib, suffix, resourcePath);
        }
    }

    private String determineBaseFolder() {
        switch (PLATFORM) {
            case "windows-x86_64":
                return "WIN64";
            case "windows-x86":
                return "WIN32";
            case "linux-x86_64":
                return "LINUX64";
            default:
                return "";
        }
    }

    private String determineSuffix() {
        return PLATFORM.startsWith("windows") ? DEFAULT_SUFFIX : SO_SUFFIX;
    }

    private void ensureDirectoryExists(File directory) throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory);
            }
        }
    }

    private void copyLibraryResource(File directory, String libName, String suffix, String resourcePath) throws IOException {
        Resource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            File outputFile = new File(directory, libName + suffix);
            copyToFile(inputStream, outputFile);
        } catch (IOException e) {
            log.error("Failed to copy library resource: {}", libName, e);
            throw e;
        }
    }

    private void copyToFile(InputStream inputStream, File outputFile) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[1024 * 100];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, length);
            }
        }
    }

    @Override
    public void destroy() {
        // Nothing to clean up.
    }
}