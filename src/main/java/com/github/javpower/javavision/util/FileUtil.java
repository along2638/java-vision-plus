package com.github.javpower.javavision.util;

import cn.hutool.core.lang.UUID;
import com.github.javpower.javavision.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class FileUtil {
    @Autowired
    private FileProperties fileProperties;

    public String getPath(MultipartFile file)throws IOException{
        // 获取原始文件名
        String originalFileName = file.getOriginalFilename();
        // 获取文件后缀
        String extension = FilenameUtils.getExtension(originalFileName);
        String target= UUID.fastUUID().toString().replace("-","")+"."+extension;
        File parentFilePath=new File(fileProperties.getUploadPath());
        if(!parentFilePath.exists()){
            parentFilePath.mkdirs();
        }
        // 构建目标文件路径
        Path targetLocation = Paths.get(fileProperties.getUploadPath()).resolve(target);
        // 将文件保存到目标路径
        Files.copy(file.getInputStream(), targetLocation);
        String filePath=targetLocation.toString();
        return filePath;
    }
}
