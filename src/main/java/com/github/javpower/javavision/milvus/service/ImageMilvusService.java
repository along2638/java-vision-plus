package com.github.javpower.javavision.milvus.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.es.response.SearchResult;
import com.github.javpower.javavision.milvus.model.Image;
import com.github.javpower.javavision.milvus.model.ImageMilvusMapper;
import com.github.javpower.javavision.service.IImageService;
import com.github.javpower.javavision.service.ImageRecordService;
import com.github.javpower.javavision.service.ImageVectorService;
import com.github.javpower.javavision.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.milvus.plus.model.vo.MilvusResp;
import org.dromara.milvus.plus.model.vo.MilvusResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service("ImageMilvusService")
@Slf4j
public class ImageMilvusService implements IImageService {

    private static final String INDEX_TYPE = "milvus";

    private final ImageMilvusMapper imageMilvusMapper;

    private final FileUtil fileUtil;

    private final ImageVectorService imageVectorService;

    private final ImageRecordService imageRecordService;

    public ImageMilvusService(ImageMilvusMapper imageMilvusMapper, FileUtil fileUtil,
                              ImageVectorService imageVectorService,
                              ImageRecordService imageRecordService) {
        this.imageMilvusMapper = imageMilvusMapper;
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
        imageMilvusMapper.insert(Collections.singletonList(search));
        imageRecordService.save(imageId, url, md5, vector.size(), INDEX_TYPE);
    }

    //图片及展示k个结果
    public List<SearchResult> search(InputStream input, int k) throws IOException, ModelException, TranslateException, OrtException {
        List<SearchResult> res = new ArrayList<>();
        List<Float> floats = imageVectorService.normalized(imageVectorService.read(input));
        MilvusResp<List<MilvusResult<Image>>> query = imageMilvusMapper.queryWrapper()
                .vector(Image::getImageVector, floats)
                .topK(k).query();
        List<MilvusResult<Image>> data = query.getData();
        for (MilvusResult<Image> datum : data) {
            String url = datum.getEntity().getUrl();
            Float distance = datum.getDistance();
            Object imageId = datum.getId();
            res.add(new SearchResult(url, imageId.toString(), distance));
        }
        return res;
    }

    @Override
    public void del(String imageId) {
        String url = imageRecordService.find(imageId).map(com.github.javpower.javavision.db.ImageRecord::getUrl).orElse(null);
        if (url != null) {
            fileUtil.delete(url);
        }
        imageMilvusMapper.deleteWrapper().eq(Image::getImageId, imageId).remove();
        imageRecordService.delete(imageId);
    }
}
