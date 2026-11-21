package com.github.javpower.javavision.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文件存储抽象。持久化资源走 {@link #upload}，需要本地文件句柄的推理场景走 {@link #materialize}。
 */
public interface StorageService {

    /**
     * 保存文件并返回可直接访问的地址（MinIO 为 http URL，本地模式为绝对路径）。
     *
     * @param file   上传文件
     * @param prefix 业务前缀，如 face / image，用作对象目录
     */
    String upload(MultipartFile file, String prefix) throws IOException;

    /**
     * 把 {@link #upload} 返回的地址物化成可读的本地文件；本地模式下原样返回。
     */
    Path materialize(String url) throws IOException;

    /**
     * 释放 {@link #materialize} 产生的临时文件（本地模式下为空操作）。
     */
    void release(Path localPath);

    /**
     * 删除远端/本地文件。
     */
    void delete(String url);

    /**
     * 是否为远端存储（用于日志与差异化处理）。
     */
    boolean isRemote();
}
