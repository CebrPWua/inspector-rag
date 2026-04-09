package my.inspectorrag.embedding.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingConfigTest {

    @Test
    void embeddingServiceShouldReturnDeterministicVectorLiteral() {
        EmbeddingConfig config = new EmbeddingConfig();
        var service = config.embeddingService();

        String v1 = service.toVectorLiteral("测试文本", 8);
        String v2 = service.toVectorLiteral("测试文本", 8);

        assertEquals(v1, v2);
        assertTrue(v1.startsWith("["));
        assertTrue(v1.endsWith("]"));
        assertEquals(8, v1.substring(1, v1.length() - 1).split(",").length);
    }
}
