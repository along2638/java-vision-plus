package com.github.javpower.javavision.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.es.response.SearchResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface IImageService {
    void add(String imageId, MultipartFile file) throws IOException, ModelException, TranslateException, OrtException;

    List<SearchResult> search(InputStream input, int k) throws IOException, ModelException, TranslateException, OrtException;

    void del(String imageId);
}
