package my.inspectorrag.docanalyzing.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingConfigTest {

    @Test
    void chunkingServiceShouldSplitByArticlePattern() {
        ChunkingConfig config = new ChunkingConfig();
        var service = config.chunkingService();

        String text = "第1条 施工单位应建立制度。\n第2条 作业前应检查设备。";
        var chunks = service.splitToChunks(text);

        assertEquals(2, chunks.size());
        assertEquals("第1条", chunks.get(0).articleNo());
        assertTrue(chunks.get(0).content().contains("施工单位"));
        assertNotNull(chunks.get(0).contentHash());
    }
}
