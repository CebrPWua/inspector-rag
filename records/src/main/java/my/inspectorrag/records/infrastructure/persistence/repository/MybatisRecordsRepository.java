package my.inspectorrag.records.infrastructure.persistence.repository;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaReplayCandidate;
import my.inspectorrag.records.domain.model.QaReplayEvidence;
import my.inspectorrag.records.domain.model.QaQualityReport;
import my.inspectorrag.records.domain.model.RejectReasonStat;
import my.inspectorrag.records.domain.repository.RecordsRepository;
import my.inspectorrag.records.infrastructure.persistence.mapper.RecordsQueryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

@Primary
@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "mybatis")
public class MybatisRecordsRepository implements RecordsRepository {

    private final RecordsQueryMapper mapper;

    public MybatisRecordsRepository(RecordsQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<QaRecordItem> listQa(int limit) {
        return mapper.listQa(limit).stream()
                .map(row -> new QaRecordItem(
                        row.qaId(),
                        row.question(),
                        row.answerStatus(),
                        row.elapsedMs(),
                        row.createdAt()
                ))
                .toList();
    }

    @Override
    public Optional<QaReplay> replay(Long qaId) {
        var header = mapper.findReplayHeader(qaId);
        if (header == null) {
            return Optional.empty();
        }
        List<QaReplayCandidate> candidates = mapper.listCandidates(qaId).stream()
                .map(row -> new QaReplayCandidate(
                        row.chunkId(),
                        row.sourceType(),
                        row.rawScore() == null ? null : row.rawScore().doubleValue(),
                        row.finalScore() == null ? null : row.finalScore().doubleValue(),
                        row.rankNo()
                ))
                .toList();
        List<QaReplayEvidence> evidences = mapper.listEvidences(qaId).stream()
                .map(row -> new QaReplayEvidence(
                        row.citeNo(),
                        row.chunkId(),
                        row.lawName(),
                        row.articleNo(),
                        row.quotedText()
                ))
                .toList();
        return Optional.of(new QaReplay(
                header.qaId(),
                header.question(),
                header.normalizedQuestion(),
                header.answer(),
                candidates,
                evidences
        ));
    }

    @Override
    public QaQualityReport qualityReport(OffsetDateTime from, OffsetDateTime to) {
        var metrics = mapper.qualityMetrics(from, to);
        if (metrics == null) {
            return new QaQualityReport(0, 0, 0, 0, null, null, null, null, List.of());
        }
        List<RejectReasonStat> reasons = mapper.topRejectReasons(from, to).stream()
                .map(it -> new RejectReasonStat(it.reasonCode(), it.cnt() == null ? 0L : it.cnt()))
                .toList();
        return new QaQualityReport(
                metrics.total() == null ? 0L : metrics.total(),
                metrics.success() == null ? 0L : metrics.success(),
                metrics.reject() == null ? 0L : metrics.reject(),
                metrics.failed() == null ? 0L : metrics.failed(),
                metrics.avgElapsedMs(),
                metrics.p95ElapsedMs(),
                metrics.avgEvidenceCount(),
                metrics.avgTop1FinalScore(),
                reasons
        );
    }
}
