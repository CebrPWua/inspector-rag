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
import static org.mockito.Mockito.never;
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
        QaApplicationService service = buildServiceWithRejectThresholds(0.10, 0.01, 0.90, 1);

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
        verify(qaRepository, never()).insertRejectedQaRecord(anyLong(), anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void askShouldThrowWhenNoRecallResults() {
        QaApplicationService service = buildDefaultService();

        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of());
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.ask("问题", null));
        assertEquals("没有在数据库中找到合适的法律法规", ex.getMessage());
        ArgumentCaptor<String> rejectReasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyString(), anyString(), rejectReasonCaptor.capture(), anyInt(), any());
        assertEquals(true, rejectReasonCaptor.getValue().startsWith("NO_EVIDENCE:"));
    }

    @Test
    void askShouldRejectWhenTop1ScoreTooLow() {
        QaApplicationService service = buildDefaultService();
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.10, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.08, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.ask("问题", null));
        assertEquals("没有在数据库中找到合适的法律法规", ex.getMessage());
        ArgumentCaptor<String> rejectReasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyString(), anyString(), rejectReasonCaptor.capture(), anyInt(), any());
        assertEquals(true, rejectReasonCaptor.getValue().startsWith("LOW_TOP1_SCORE:"));
    }

    @Test
    void askShouldRejectWhenScoreGapTooSmallAndTop1NotConfident() {
        QaApplicationService service = buildDefaultService();
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.69, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.68, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.69, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.68, 1, 1, "v1")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.ask("问题", null));
        assertEquals("没有在数据库中找到合适的法律法规", ex.getMessage());
        ArgumentCaptor<String> rejectReasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyString(), anyString(), rejectReasonCaptor.capture(), anyInt(), any());
        assertEquals(true, rejectReasonCaptor.getValue().startsWith("LOW_SCORE_GAP:"));
    }

    @Test
    void askShouldRejectWhenEvidenceCountIsInsufficient() {
        QaApplicationService service = buildServiceWithRejectThresholds(0.0, 0.0, 1.0, 2);
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(9L, "法规K", "第9条", "关键词命中内容", 0.95, 2, 2, "v2")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.ask("关键词命中", null));
        assertEquals("没有在数据库中找到合适的法律法规", ex.getMessage());
        ArgumentCaptor<String> rejectReasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyString(), anyString(), rejectReasonCaptor.capture(), anyInt(), any());
        assertEquals(true, rejectReasonCaptor.getValue().startsWith("INSUFFICIENT_EVIDENCE_COUNT:"));
    }

    @Test
    void askShouldPassWhenVectorOnlyScoreIsHighEnoughUnderVectorOnlyThreshold() {
        QaApplicationService service = buildDefaultService();
        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(100L, "法规高分", "第10条", "内容高分", 0.82, 1, 1, "v1"),
                new RecallCandidate(101L, "法规次高分", "第11条", "内容次高分", 0.79, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("塔吊限位器失效需要立即停机吗", null);
        assertNotNull(response.qaId());
        assertEquals(2, response.evidences().size());
        verify(qaRepository, never()).insertRejectedQaRecord(anyLong(), anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void askShouldRejectWhenVectorOnlyScoreIsBelowVectorOnlyThreshold() {
        QaApplicationService service = buildDefaultService();
        when(recallService.recall(anyString(), anyInt(), any())).thenReturn(List.of(
                new RecallCandidate(110L, "法规低分", "第20条", "内容低分", 0.62, 1, 1, "v1"),
                new RecallCandidate(111L, "法规次低分", "第21条", "内容次低分", 0.61, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.ask("临边防护栏杆要求", null));
        assertEquals("没有在数据库中找到合适的法律法规", ex.getMessage());
        ArgumentCaptor<String> rejectReasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyString(), anyString(), rejectReasonCaptor.capture(), anyInt(), any());
        assertEquals(true, rejectReasonCaptor.getValue().startsWith("LOW_TOP1_SCORE:"));
        assertEquals(true, rejectReasonCaptor.getValue().contains("threshold=0.7200"));
    }

    @Test
    void getQaShouldReturnQaDetailWithEvidence() {
        QaApplicationService service = buildDefaultService();

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
        QaApplicationService service = buildServiceWithRejectThresholds(0.10, 0.01, 0.90, 1, 2, "jdbc");

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
        assertEquals(true, sourceTypes.contains("hybrid"));
    }

    @Test
    void askShouldUseKeywordRecallWhenVectorIsEmpty() {
        QaApplicationService service = buildServiceWithRejectThresholds(0.10, 0.01, 0.90, 1);

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
        QaApplicationService service = buildServiceWithRejectThresholds(0.10, 0.01, 0.90, 1, 3, "springai");

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
        QaApplicationService service = buildServiceWithRejectThresholds(0.10, 0.01, 0.90, 1);

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

    private QaApplicationService buildDefaultService() {
        return buildServiceWithRejectThresholds(0.55, 0.08, 0.70, 2);
    }

    private QaApplicationService buildServiceWithRejectThresholds(
            double minTop1Score,
            double minTopGap,
            double minConfidentScore,
            int minEvidenceCount
    ) {
        return buildServiceWithRejectThresholds(minTop1Score, 0.72, minTopGap, minConfidentScore, minEvidenceCount, 3, "jdbc", true);
    }

    private QaApplicationService buildServiceWithRejectThresholds(
            double minTop1Score,
            double minTopGap,
            double minConfidentScore,
            int minEvidenceCount,
            int finalTopN,
            String retrievalProvider
    ) {
        return buildServiceWithRejectThresholds(
                minTop1Score,
                0.72,
                minTopGap,
                minConfidentScore,
                minEvidenceCount,
                finalTopN,
                retrievalProvider,
                true
        );
    }

    private QaApplicationService buildServiceWithRejectThresholds(
            double minTop1Score,
            double minTop1ScoreVectorOnly,
            double minTopGap,
            double minConfidentScore,
            int minEvidenceCount,
            int finalTopN,
            String retrievalProvider,
            boolean scoreNormalizationEnabled
    ) {
        return new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                "text-embedding-3-small",
                2,
                retrievalProvider,
                4,
                finalTopN,
                "simple",
                scoreNormalizationEnabled,
                0.55,
                0.25,
                0.10,
                0.10,
                minTop1Score,
                minTop1ScoreVectorOnly,
                minTopGap,
                minConfidentScore,
                minEvidenceCount
        );
    }
}
