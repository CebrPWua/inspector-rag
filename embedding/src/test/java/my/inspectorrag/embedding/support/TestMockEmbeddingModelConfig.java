package my.inspectorrag.embedding.support;

import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestMockEmbeddingModelConfig {

    private static final int DIMENSION = 1536;

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
        when(model.embed(any(String.class))).thenAnswer(invocation -> toEmbedding(invocation.getArgument(0)));
        when(model.embed(any(List.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Document> docs = invocation.getArgument(0);
            List<float[]> embeddings = new ArrayList<>(docs.size());
            for (Document doc : docs) {
                embeddings.add(toEmbedding(doc == null || doc.getText() == null ? "" : doc.getText()));
            }
            return embeddings;
        });
        return model;
    }

    private float[] toEmbedding(String text) {
        float[] vector = new float[DIMENSION];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int idx = i % DIMENSION;
            float value = (float) ((bytes[i] & 0xFF) / 255.0);
            vector[idx] += (i % 2 == 0 ? value : -value);
        }
        return vector;
    }
}
