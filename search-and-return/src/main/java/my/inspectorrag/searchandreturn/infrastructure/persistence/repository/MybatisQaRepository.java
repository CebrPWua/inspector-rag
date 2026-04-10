package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaCommandMapper;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaQueryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "mybatis")
public class MybatisQaRepository implements QaRepository {

    private final QaCommandMapper commandMapper;
    private final QaQueryMapper queryMapper;

    public MybatisQaRepository(QaCommandMapper commandMapper, QaQueryMapper queryMapper) {
        this.commandMapper = commandMapper;
        this.queryMapper = queryMapper;
    }

    @Override
    public List<RecallCandidate> vectorRecall(String vectorLiteral, int topK) {
        return queryMapper.vectorRecall(vectorLiteral, topK).stream()
                .map(row -> new RecallCandidate(
                        row.chunkId(),
                        row.lawName(),
                        row.articleNo(),
                        truncate(row.content()),
                        row.score(),
                        row.pageStart(),
                        row.pageEnd(),
                        row.versionNo()
                ))
                .toList();
    }

    @Override
    public void insertQaRecord(Long id, String question, String normalizedQuestion, String answer, int elapsedMs, OffsetDateTime now) {
        commandMapper.insertQaRecord(id, question, normalizedQuestion, answer, elapsedMs, now);
    }

    @Override
    public void insertRetrievalSnapshot(Long id, Long qaId, String modelName, int topK, int topN, OffsetDateTime now) {
        commandMapper.insertRetrievalSnapshot(id, qaId, modelName, topK, topN, now);
    }

    @Override
    public void insertCandidate(Long id, Long qaId, RecallCandidate candidate, int rankNo, OffsetDateTime now) {
        commandMapper.insertCandidate(id, qaId, candidate.chunkId(), candidate.score(), rankNo, now);
    }

    @Override
    public void insertEvidence(Long id, Long qaId, RecallCandidate candidate, int citeNo, OffsetDateTime now) {
        commandMapper.insertEvidence(
                id,
                qaId,
                candidate.chunkId(),
                citeNo,
                candidate.content(),
                candidate.lawName(),
                candidate.articleNo(),
                candidate.pageStart(),
                candidate.pageEnd(),
                candidate.versionNo(),
                now
        );
    }

    @Override
    public Optional<QaDetail> findQaDetail(Long qaId) {
        return Optional.ofNullable(queryMapper.findQaDetail(qaId))
                .map(row -> new QaDetail(
                        row.qaId(),
                        row.question(),
                        row.normalizedQuestion(),
                        row.answer(),
                        row.answerStatus(),
                        row.createdAt(),
                        findQaEvidences(qaId)
                ));
    }

    @Override
    public List<RecallCandidate> findQaEvidences(Long qaId) {
        return queryMapper.findQaEvidences(qaId).stream()
                .map(row -> new RecallCandidate(
                        row.chunkId(),
                        row.lawName(),
                        row.articleNo(),
                        row.quotedText(),
                        row.finalScore() == null ? null : row.finalScore().doubleValue(),
                        row.pageStart(),
                        row.pageEnd(),
                        row.fileVersion()
                ))
                .toList();
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 500 ? content : content.substring(0, 500);
    }
}
