package com.github.javpower.javavision.vectorex.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.db.ImageRecord;
import com.github.javpower.javavision.es.response.SearchResult;
import com.github.javpower.javavision.service.IImageService;
import com.github.javpower.javavision.service.ImageRecordService;
import com.github.javpower.javavision.service.ImageVectorService;
import com.github.javpower.javavision.util.FileUtil;
import com.github.javpower.javavision.vectorex.mapper.ImageVectoRexMapper;
import com.github.javpower.javavision.vectorex.model.Image;
import io.github.javpower.vectorexbootstater.core.VectoRexResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service("ImageVectoRexService")
@Slf4j
public class ImageVectoRexService implements IImageService {

    private static final String INDEX_TYPE = "vectorex";

    private final ImageVectoRexMapper imageVectoRexMapper;

    private final FileUtil fileUtil;

    private final ImageVectorService imageVectorService;

    private final ImageRecordService imageRecordService;

    public ImageVectoRexService(ImageVectoRexMapper imageVectoRexMapper, FileUtil fileUtil,
                                ImageVectorService imageVectorService,
                                ImageRecordService imageRecordService) {
        this.imageVectoRexMapper = imageVectoRexMapper;
        this.fileUtil = fileUtil;
        this.imageVectorService = imageVectorService;
        this.imageRecordService = imageRecordService;
    }

    public void add(String imageId, MultipartFile file) throws IOException, ModelException, TranslateException, OrtException {
        // 先按内容提特征（命中 Redis 缓存时完全跳过推理），再上传原图
        byte[] bytes = file.getBytes();
        String md5 = imageVectorService.md5(bytes);
        List<Float> vector = imageVectorService.normalized(bytes);
        String url = fileUtil.upload(file, "image");

        Image search = new Image();
        search.setImageId(imageId);
        search.setUrl(url);
        search.setImageVector(vector);
        imageVectoRexMapper.insert(Collections.singletonList(search));
        // 元数据写 PG：列表查询、删除定位原图都靠它
        imageRecordService.save(imageId, url, md5, vector.size(), INDEX_TYPE);
    }

    //图片及展示k个结果
    public List<SearchResult> search(InputStream input, int k) throws IOException, ModelException, TranslateException, OrtException {
        List<SearchResult> res = new ArrayList<>();
        List<Float> floats = imageVectorService.normalized(imageVectorService.read(input));
        List<VectoRexResult<Image>> data = imageVectoRexMapper.queryWrapper()
                .vector(Image::getImageVector, floats)
                .topK(k).query();
        for (VectoRexResult<Image> datum : data) {
            Image entity = datum.getEntity();
            String url = entity.getUrl();
            Float distance = datum.getScore();
            Object imageId = datum.getEntity().getImageId();
            res.add(new SearchResult(url, imageId.toString(), distance));
        }
        return res;
    }

    @Override
    public void del(String imageId) {
        // 先按 id 取出 URL，把对象存储里的原图一并清掉，避免产生孤儿文件
        String url = imageRecordService.find(imageId).map(ImageRecord::getUrl).orElse(null);
        if (url == null) {
            VectoRexResult<?> result = imageVectoRexMapper.getById(imageId);
            if (result != null && result.getEntity() instanceof Image) {
                url = ((Image) result.getEntity()).getUrl();
            }
        }
        if (url != null) {
            fileUtil.delete(url);
        }
        imageVectoRexMapper.removeById(imageId);
        imageRecordService.delete(imageId);
    }
}
