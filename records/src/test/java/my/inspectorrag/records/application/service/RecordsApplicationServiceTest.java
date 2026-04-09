package my.inspectorrag.records.application.service;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaReplayCandidate;
import my.inspectorrag.records.domain.model.QaReplayEvidence;
import my.inspectorrag.records.domain.repository.RecordsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordsApplicationServiceTest {

    @Mock
    private RecordsRepository recordsRepository;

    @Test
    void listQaShouldReturnMappedDtos() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        when(recordsRepository.listQa(20)).thenReturn(List.of(
                new QaRecordItem(1L, "问题", "success", 100, OffsetDateTime.now())
        ));

        var list = service.listQa(20);
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).qaId());
    }

    @Test
    void replayShouldThrowWhenQaNotFound() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        when(recordsRepository.replay(7L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.replay(7L));
    }

    @Test
    void replayShouldReturnMappedReplayDto() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        when(recordsRepository.replay(7L)).thenReturn(Optional.of(
                new QaReplay(
                        7L,
                        "原问题",
                        "标准问题",
                        "答案",
                        List.of(new QaReplayCandidate(11L, "vector", 0.8, 0.9, 1)),
                        List.of(new QaReplayEvidence(1, 11L, "法规", "第1条", "引用"))
                )
        ));

        var replay = service.replay(7L);
        assertEquals(7L, replay.qaId());
        assertEquals(1, replay.candidates().size());
        assertEquals(1, replay.evidences().size());
    }
}
