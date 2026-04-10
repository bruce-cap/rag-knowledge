package com.example.ragbackend.service.impl;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import dev.langchain4j.model.output.Response;

@Slf4j
@Service
public class DashScopeEmbeddingServiceImpl implements DashScopeEmbeddingService {

    private final TextEmbedding textEmbedding = new TextEmbedding();

    @Value("${langchain4j.dashscope.embedding-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.dashscope.embedding-model.model-name:text-embedding-v4}")
    private String modelName;

    @Value("${langchain4j.dashscope.embedding-model.batch-size:10}")
    private Integer batchSize;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return Response.from(embedSegments(textSegments));
    }

    @Override
    public List<Embedding> embedSegments(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            log.debug("embedSegments called with empty segments");
            return List.of();
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DashScope API key is missing. Please configure DASHSCOPE_API_KEY");
        }

        Constants.apiKey = apiKey;

        List<Embedding> embeddings = new ArrayList<>();
        int actualBatchSize = batchSize == null || batchSize <= 0 ? 10 : batchSize;

        log.info("开始批量生成 Embedding, 总segments={}, 批次大小={}, model={}",
                segments.size(), actualBatchSize, modelName);

        int batchIndex = 0;
        for (int start = 0; start < segments.size(); start += actualBatchSize) {
            int end = Math.min(start + actualBatchSize, segments.size());
            List<TextSegment> batch = segments.subList(start, end);
            embeddings.addAll(embedBatch(batch));
            batchIndex++;
            log.debug("批次 {}/{} 完成, batchStart={}, batchEnd={}",
                    batchIndex, (segments.size() + actualBatchSize - 1) / actualBatchSize, start, end);
        }

        log.info("Embedding 生成完成, 总segments={}, 生成embeddings={}", segments.size(), embeddings.size());

        if (embeddings.size() != segments.size()) {
            throw new IllegalStateException("DashScope embedding count does not match segment count");
        }
        return embeddings;
    }

    private List<Embedding> embedBatch(List<TextSegment> segments) {
        List<String> texts = segments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());

        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(modelName)
                    .texts(texts)
                    .build();

            TextEmbeddingResult result = textEmbedding.call(param);
            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new IllegalStateException("DashScope returned empty embedding result");
            }

            return result.getOutput().getEmbeddings().stream()
                    .sorted(Comparator.comparing(TextEmbeddingResultItem::getTextIndex))
                    .map(TextEmbeddingResultItem::getEmbedding)
                    .map(vector -> Embedding.from(toFloatList(vector)))
                    .collect(Collectors.toList());
        } catch (ApiException | NoApiKeyException e) {
            log.error("DashScope embedding request failed, model={}, error={}", modelName, e.getMessage(), e);
            throw new IllegalStateException("DashScope embedding request failed: " + e.getMessage(), e);
        }
    }

    private List<Float> toFloatList(List<Double> vector) {
        List<Float> result = new ArrayList<>(vector.size());
        for (Double value : vector) {
            result.add(value == null ? 0F : value.floatValue());
        }
        return result;
    }
}
