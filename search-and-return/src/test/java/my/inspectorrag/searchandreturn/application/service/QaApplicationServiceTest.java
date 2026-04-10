package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        QaApplicationService service = new QaApplicationService(qaRepository, recallService, answerGenerator, "text-embedding-3-small", 2);

        when(answerGenerator.generate(anyString(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), eq(2))).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.91, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.87, 1, 1, "v1")
        ));

        var response = service.ask("  高处作业防护  ");

        assertNotNull(response.qaId());
        assertEquals("高处作业防护", response.normalizedQuestion());
        assertEquals(2, response.evidences().size());
        verify(qaRepository).insertQaRecord(anyLong(), anyString(), anyString(), anyString(), anyInt(), any());
        verify(qaRepository, times(2)).insertCandidate(anyLong(), anyLong(), any(), anyInt(), any());
        verify(qaRepository, times(2)).insertEvidence(anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void askShouldThrowWhenNoRecallResults() {
        QaApplicationService service = new QaApplicationService(qaRepository, recallService, answerGenerator, "text-embedding-3-small", 2);

        when(recallService.recall(anyString(), eq(2))).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.ask("问题"));
    }

    @Test
    void getQaShouldReturnQaDetailWithEvidence() {
        QaApplicationService service = new QaApplicationService(qaRepository, recallService, answerGenerator, "text-embedding-3-small", 2);

        OffsetDateTime now = OffsetDateTime.now();
        var evidence = new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.9, 1, 1, "v1");
        when(qaRepository.findQaDetail(9L)).thenReturn(Optional.of(
                new QaDetail(9L, "原问题", "标准问题", "答案", "success", now, List.of(evidence))
        ));

        var detail = service.getQa(9L);
        assertEquals(9L, detail.qaId());
        assertEquals("标准问题", detail.normalizedQuestion());
        assertEquals(1, detail.evidences().size());
    }
}
