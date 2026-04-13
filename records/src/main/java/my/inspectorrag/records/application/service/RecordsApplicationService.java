package my.inspectorrag.records.application.service;

import my.inspectorrag.records.domain.repository.RecordsRepository;
import my.inspectorrag.records.interfaces.dto.QaRecordItemDto;
import my.inspectorrag.records.interfaces.dto.QaQualityReportDto;
import my.inspectorrag.records.interfaces.dto.QaReplayCandidateDto;
import my.inspectorrag.records.interfaces.dto.QaReplayDto;
import my.inspectorrag.records.interfaces.dto.QaReplayEvidenceDto;
import my.inspectorrag.records.interfaces.dto.RejectReasonStatDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RecordsApplicationService {

    private final RecordsRepository recordsRepository;

    public RecordsApplicationService(RecordsRepository recordsRepository) {
        this.recordsRepository = recordsRepository;
    }

    @Transactional(readOnly = true)
    public List<QaRecordItemDto> listQa(int limit) {
        return recordsRepository.listQa(limit).stream()
                .map(it -> new QaRecordItemDto(toIdString(it.qaId()), it.question(), it.answerStatus(), it.elapsedMs(), it.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public QaReplayDto replay(Long qaId) {
        var replay = recordsRepository.replay(qaId)
                .orElseThrow(() -> new IllegalArgumentException("qa replay not found: " + qaId));
        return new QaReplayDto(
                toIdString(replay.qaId()),
                replay.question(),
                replay.normalizedQuestion(),
                replay.answer(),
                replay.candidates().stream()
                        .map(c -> new QaReplayCandidateDto(toIdString(c.chunkId()), c.sourceType(), c.rawScore(), c.finalScore(), c.rankNo()))
                        .toList(),
                replay.evidences().stream()
                        .map(e -> new QaReplayEvidenceDto(e.citeNo(), toIdString(e.chunkId()), e.lawName(), e.articleNo(), e.quotedText()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public QaQualityReportDto qualityReport(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime end = to == null ? OffsetDateTime.now() : to;
        OffsetDateTime start = from == null ? end.minusDays(7) : from;
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }

        var report = recordsRepository.qualityReport(start, end);
        double rejectRate = report.total() == 0 ? 0d : (double) report.reject() / report.total();
        double failedRate = report.total() == 0 ? 0d : (double) report.failed() / report.total();

        return new QaQualityReportDto(
                start,
                end,
                report.total(),
                report.success(),
                report.reject(),
                report.failed(),
                rejectRate,
                failedRate,
                report.avgElapsedMs(),
                report.p95ElapsedMs(),
                report.avgEvidenceCount(),
                report.avgTop1FinalScore(),
                report.topRejectReasons().stream()
                        .map(it -> new RejectReasonStatDto(it.reasonCode(), it.count()))
                        .toList()
        );
    }

    private String toIdString(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
