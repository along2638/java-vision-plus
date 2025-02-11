package com.github.javpower.javavision.vectorex.service;

import ai.djl.ModelException;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.es.response.SearchResult;
import com.github.javpower.javavision.service.IImageService;
import com.github.javpower.javavision.util.FileUtil;
import com.github.javpower.javavision.util.ImageFeatureUtil;
import com.github.javpower.javavision.util.ImageUtil;
import com.github.javpower.javavision.vectorex.mapper.ImageVectoRexMapper;
import com.github.javpower.javavision.vectorex.model.Image;
import io.github.javpower.vectorexbootstater.core.VectoRexResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service("ImageVectoRexService")
@Slf4j
public class ImageVectoRexService implements IImageService {
    @Autowired
    private ImageVectoRexMapper imageVectoRexMapper;
    @Autowired
    private  FileUtil fileUtil;

    public void add(String imageId,MultipartFile file) throws IOException, ModelException, TranslateException, OrtException {
        List<Image> imageSearchList=new ArrayList<>();
        Image search=new Image();
        String path = fileUtil.getPath(file);
        search.setImageId(imageId);
        search.setUrl(path);
        imageSearchList.add(search);
        batchAdd(imageSearchList);
    }
    //图片及展示k个结果
    public List<SearchResult> search(InputStream input,int k) throws IOException, ModelException, TranslateException, OrtException {
        List<SearchResult> res=new ArrayList<>();
        try (InputStream inputStream = input) {
            ai.djl.modality.cv.Image image = ImageFactory.getInstance().fromInputStream(inputStream);
            float[] vector = ImageFeatureUtil.runOcr(image);
            List<Float> floatList = new ArrayList<>();
            // 遍历数组并将每个元素添加到列表中
            for (float f : vector) {
                floatList.add(f);
            }
            List<Float> floats = ImageUtil.normalizeVector(floatList);
            List<VectoRexResult<Image>> data = imageVectoRexMapper.queryWrapper()
                    .vector(Image::getImageVector, floats)
                    .topK(k).query();
            for (VectoRexResult<Image> datum : data) {
                Image entity = datum.getEntity();
                String url = entity.getUrl();
                Float distance = datum.getScore();
                Object imageId = datum.getEntity().getImageId();
                SearchResult result=new SearchResult(url,imageId.toString(),distance);
                res.add(result);
            }
        }
        return res;
    }

    @Override
    public void del(String imageId) {
        imageVectoRexMapper.removeById(imageId);
    }

    private void batchAdd(List<Image> imageSearchList) throws IOException, ModelException, TranslateException, OrtException {
        //批量上传请求
        for (Image imageSearch : imageSearchList) {
            float[] vector = ImageFeatureUtil.runOcr(imageSearch.getUrl());
            List<Float> floatList = new ArrayList<>();
            // 遍历数组并将每个元素添加到列表中
            for (float f : vector) {
                floatList.add(f);
            }
            List<Float> floats = ImageUtil.normalizeVector(floatList);
            imageSearch.setImageVector(floats);
        }
        imageVectoRexMapper.insert(imageSearchList);
    }
}
