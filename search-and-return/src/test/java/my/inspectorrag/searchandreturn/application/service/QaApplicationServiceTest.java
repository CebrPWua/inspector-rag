package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import my.inspectorrag.searchandreturn.interfaces.dto.AskFilters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaApplicationServiceTest {

    @Mock
    private QaRepository qaRepository;
    @Mock
    private RecallService recallService;
    @Mock
    private AnswerGenerator answerGenerator;

    @Test
    void askShouldRecallAndPersistQaRecords() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "jdbc",
                4,
                3,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.91, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.87, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());
        var response = service.ask("  高处作业防护  ", null);

        assertNotNull(response.qaId());
        assertEquals("高处作业防护", response.normalizedQuestion());
        assertEquals(2, response.evidences().size());
        verify(qaRepository).insertQaRecord(anyLong(), anyString(), anyString(), anyString(), anyInt(), any());
        verify(qaRepository, times(2)).insertCandidate(anyLong(), anyLong(), anyLong(), anyString(), any(), any(), any(), anyInt(), any());
        verify(qaRepository, times(2)).insertEvidence(anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void askShouldThrowWhenNoRecallResults() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "jdbc",
                4,
                3,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of());
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.ask("问题", null));
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void getQaShouldReturnQaDetailWithEvidence() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "jdbc",
                4,
                3,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        OffsetDateTime now = OffsetDateTime.now();
        var evidence = new QaEvidence(1, 1L, "法规A", "第1条", "内容A", "vector", 0.9, 1, 1, "v1");
        when(qaRepository.findQaDetail(9L)).thenReturn(Optional.of(
                new QaDetail(9L, "原问题", "标准问题", "答案", "success", now, List.of(evidence))
        ));

        var detail = service.getQa(9L);
        assertEquals(9L, detail.qaId());
        assertEquals("标准问题", detail.normalizedQuestion());
        assertEquals(1, detail.evidences().size());
        assertEquals("vector", detail.evidences().get(0).sourceType());
    }

    @Test
    void askShouldMergeVectorAndKeywordAsHybridAndRespectTopN() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "jdbc",
                4,
                2,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.90, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.70, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.95, 1, 1, "v1"),
                new RecallCandidate(3L, "法规C", "第3条", "内容C", 0.80, 1, 1, "v1")
        ));

        service.ask("高处作业防护", null);

        ArgumentCaptor<String> sourceTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(qaRepository, times(2)).insertCandidate(
                anyLong(), anyLong(), anyLong(), sourceTypeCaptor.capture(), any(), any(), any(), anyInt(), any()
        );
        List<String> sourceTypes = sourceTypeCaptor.getAllValues();
        assertEquals(2, sourceTypes.size());
        // topN=2 时，命中融合后应至少包含一个 hybrid
        assertEquals(true, sourceTypes.contains("hybrid"));
    }

    @Test
    void askShouldUseKeywordRecallWhenVectorIsEmpty() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "jdbc",
                4,
                3,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of());
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(9L, "法规K", "第9条", "关键词命中内容", 0.88, 2, 2, "v2")
        ));

        var response = service.ask("关键词命中", null);

        assertEquals(1, response.evidences().size());
        assertEquals("keyword", response.evidences().get(0).sourceType());
    }

    @Test
    void askShouldApplyMetadataFilterForSpringAiRecall() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "springai",
                4,
                3,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(11L, "法规A", "第1条", "内容A", 0.90, 1, 1, "v1"),
                new RecallCandidate(12L, "法规B", "第2条", "内容B", 0.80, 1, 1, "v1")
        ));
        when(qaRepository.filterChunkIdsByMetadata(anyList(), any())).thenReturn(Set.of(12L));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("springai过滤", null);

        assertEquals(1, response.evidences().size());
        assertEquals(12L, response.evidences().get(0).chunkId());
        verify(qaRepository).filterChunkIdsByMetadata(anyList(), any());
    }

    @Test
    void askShouldInvokeIndustryTagMatchWhenIndustryFilterProvided() {
        QaApplicationService service = new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                "jdbc",
                4,
                3,
                "simple",
                0.55,
                0.25,
                0.10,
                0.10
        );

        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(21L, "法规A", "第1条", "内容A", 0.92, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());
        when(qaRepository.findChunkIdsByIndustryTags(anyList(), anyList())).thenReturn(Set.of(21L));

        AskFilters filters = new AskFilters(
                List.of("建筑施工"),
                List.of("regulation"),
                "民航局",
                LocalDate.of(2026, 4, 10)
        );
        service.ask("行业标签过滤", filters);

        verify(qaRepository).findChunkIdsByIndustryTags(anyList(), anyList());
    }
}
