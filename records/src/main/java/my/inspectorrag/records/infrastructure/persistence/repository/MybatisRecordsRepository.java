package my.inspectorrag.records.infrastructure.persistence.repository;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaReplayCandidate;
import my.inspectorrag.records.domain.model.QaReplayEvidence;
import my.inspectorrag.records.domain.repository.RecordsRepository;
import my.inspectorrag.records.infrastructure.persistence.mapper.RecordsQueryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
}
