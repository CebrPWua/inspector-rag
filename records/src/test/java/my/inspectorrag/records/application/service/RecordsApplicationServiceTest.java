package my.inspectorrag.records.application.service;

import my.inspectorrag.records.domain.model.QaQualityReport;
import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaReplayCandidate;
import my.inspectorrag.records.domain.model.QaReplayEvidence;
import my.inspectorrag.records.domain.model.RejectReasonStat;
import my.inspectorrag.records.domain.repository.RecordsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
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
        assertEquals("1", list.get(0).qaId());
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
        assertEquals("7", replay.qaId());
        assertEquals(1, replay.candidates().size());
        assertEquals(1, replay.evidences().size());
    }

    @Test
    void qualityReportShouldUseDefaultLast7DaysWindow() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        when(recordsRepository.qualityReport(any(), any()))
                .thenReturn(new QaQualityReport(
                        10,
                        6,
                        3,
                        1,
                        350.5,
                        820.2,
                        2.4,
                        0.73,
                        List.of(new RejectReasonStat("LOW_TOP1_SCORE", 2))
                ));

        var report = service.qualityReport(null, null);

        assertEquals(10, report.total());
        assertEquals(0.3, report.rejectRate(), 0.000001);
        assertEquals(0.1, report.failedRate(), 0.000001);
        assertEquals(1, report.topRejectReasons().size());
        assertEquals("LOW_TOP1_SCORE", report.topRejectReasons().get(0).reasonCode());

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(recordsRepository).qualityReport(fromCaptor.capture(), toCaptor.capture());
        long windowHours = java.time.Duration.between(fromCaptor.getValue(), toCaptor.getValue()).toHours();
        assertEquals(168L, windowHours);
    }

    @Test
    void qualityReportShouldUseExplicitWindow() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        OffsetDateTime from = OffsetDateTime.parse("2026-04-01T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-10T00:00:00+08:00");
        when(recordsRepository.qualityReport(from, to)).thenReturn(new QaQualityReport(
                0, 0, 0, 0, null, null, null, null, List.of()
        ));

        var report = service.qualityReport(from, to);

        assertEquals(from, report.from());
        assertEquals(to, report.to());
        assertEquals(0d, report.rejectRate(), 0.000001);
        assertEquals(0d, report.failedRate(), 0.000001);
        verify(recordsRepository).qualityReport(from, to);
    }

    @Test
    void qualityReportShouldRejectInvalidWindow() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        OffsetDateTime from = OffsetDateTime.parse("2026-04-10T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-09T00:00:00+08:00");

        assertThrows(IllegalArgumentException.class, () -> service.qualityReport(from, to));
    }

    @Test
    void qualityReportShouldKeepRejectReasonOrderAndCount() {
        RecordsApplicationService service = new RecordsApplicationService(recordsRepository);
        OffsetDateTime from = OffsetDateTime.parse("2026-04-01T00:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-10T00:00:00+08:00");
        when(recordsRepository.qualityReport(from, to)).thenReturn(new QaQualityReport(
                8,
                2,
                5,
                1,
                400.0,
                900.0,
                2.0,
                0.6,
                List.of(
                        new RejectReasonStat("LOW_TOP1_SCORE", 3),
                        new RejectReasonStat("LOW_SCORE_GAP", 2)
                )
        ));

        var report = service.qualityReport(from, to);
        assertEquals(2, report.topRejectReasons().size());
        assertEquals("LOW_TOP1_SCORE", report.topRejectReasons().get(0).reasonCode());
        assertEquals(3L, report.topRejectReasons().get(0).count());
        assertEquals("LOW_SCORE_GAP", report.topRejectReasons().get(1).reasonCode());
        assertEquals(2L, report.topRejectReasons().get(1).count());
    }
}
