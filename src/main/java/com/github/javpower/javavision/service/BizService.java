package com.github.javpower.javavision.service;

import ai.djl.ModelException;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.github.javpower.javavision.entity.*;
import com.github.javpower.javavision.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

/**
 * OCR / 目标检测的业务编排：临时文件落盘、调用推理、用完即删。
 */
@Service
@Slf4j
public class BizService {

    private final FileUtil fileUtil;

    public BizService(FileUtil fileUtil) {
        this.fileUtil = fileUtil;
    }

    public String ocr(OcrParam param, MultipartFile file, HttpServletRequest request) throws Exception {
        String imgPath = fileUtil.getTempPath(file);
        try {
            OcrResult ONNXResult = OcrUtil.runOcr(imgPath);
            StrBuilder strBuilder=new StrBuilder();
            for (TextBlock textBlock : ONNXResult.getTextBlocks()) {
                String text = textBlock.getText();
                if(StrUtil.isNotEmpty(text)){
                    strBuilder.append(text.trim()).append("\n");
                }
            }
            String s = strBuilder.toString();
            if(param==null||StrUtil.isEmpty(param.getKey())){
                return s;
            }
            List<String> content = param.getContent();
            String temp=(content==null||content.size()==0)?"":"【"+StrUtil.join("、",content)+"】";
            String chat = OpenAIAPI.chat("提取出我发的数据里面的"+temp+"值，要求key-value的形式输出JSON类型的文本"+(temp.length()>0?",其中 key要严格按照【】里的":"")+"。\n"+s);
            log.info("AI 提取结果：{}", chat);
            return chat;
        } finally {
            fileUtil.deleteIfNeeded(imgPath);
        }
    }

    public String yolo(OcrParam param, MultipartFile file, HttpServletRequest request) throws Exception {
        String imgPath = fileUtil.getTempPath(file);
        try {
            List<Detection> yolo = Yolo8Util.runOcr(imgPath);
            StringBuffer stringBuffer=new StringBuffer();
            yolo.forEach(v->{
                stringBuffer.append(v.label+"\n");
            });
            return stringBuffer.toString();
        } finally {
            fileUtil.deleteIfNeeded(imgPath);
        }
    }

    public List<WordBlock> jsonPpWorld(OcrParam param, MultipartFile file, HttpServletRequest request) throws Exception {
        String imgPath = fileUtil.getTempPath(file);
        try {
            return OcrV4Util.runOcr(imgPath);
        } finally {
            fileUtil.deleteIfNeeded(imgPath);
        }
    }

    public String ppWorld(OcrParam param, MultipartFile file, HttpServletRequest request) throws Exception {
        List<WordBlock> wordBlocks = jsonPpWorld(param, file, request);
        StrBuilder strBuilder=new StrBuilder();
        for (WordBlock textBlock : wordBlocks) {
            String text = textBlock.getText();
            if(StrUtil.isNotEmpty(text)){
                strBuilder.append(text.trim()).append("\n");
            }
        }
        return strBuilder.toString();
    }

    public List<Detection> jsonYolo(Object o, MultipartFile file, HttpServletRequest request) throws IOException, OrtException {
        String imgPath = fileUtil.getTempPath(file);
        try {
            return Yolo8Util.runOcr(imgPath);
        } finally {
            fileUtil.deleteIfNeeded(imgPath);
        }
    }

    public List<TextBlock> jsonOcr(OcrParam param, MultipartFile file, HttpServletRequest request) throws IOException {
        String imgPath = fileUtil.getTempPath(file);
        try {
            return OcrUtil.runOcr(imgPath).getTextBlocks();
        } finally {
            fileUtil.deleteIfNeeded(imgPath);
        }
    }

    public DetectedObjects fireYolo(Object o, MultipartFile file, HttpServletRequest request) throws IOException, ModelException, TranslateException {
        String path = fileUtil.getTempPath(file);
        try {
            return FireSmokeDetectUtil.runOcr(path);
        } finally {
            fileUtil.deleteIfNeeded(path);
        }
    }

    public DetectedObjects reflective(Object o, MultipartFile file, HttpServletRequest request) throws IOException, ModelException, TranslateException {
        String path = fileUtil.getTempPath(file);
        try {
            return ReflectiveVestDetectUtil.runOcr(path);
        } finally {
            fileUtil.deleteIfNeeded(path);
        }
    }

    public DetectedObjects construction(Object o, MultipartFile file, HttpServletRequest request) throws ModelException, TranslateException, IOException {
        String path = fileUtil.getTempPath(file);
        try {
            return ConstructionDetectUtil.runOcr(path);
        } finally {
            fileUtil.deleteIfNeeded(path);
        }
    }
}
