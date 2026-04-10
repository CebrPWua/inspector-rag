package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import my.inspectorrag.searchandreturn.interfaces.dto.AskResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.EvidenceDto;
import my.inspectorrag.searchandreturn.interfaces.dto.QaDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QaApplicationService {

    private final QaRepository qaRepository;
    private final RecallService recallService;
    private final AnswerGenerator answerGenerator;
    private final String embeddingModelName;
    private final int topK;

    public QaApplicationService(
            QaRepository qaRepository,
            RecallService recallService,
            AnswerGenerator answerGenerator,
            @Value("${inspector.embedding.model-name}") String embeddingModelName,
            @Value("${inspector.embedding.top-k}") int topK
    ) {
        this.qaRepository = qaRepository;
        this.recallService = recallService;
        this.answerGenerator = answerGenerator;
        this.embeddingModelName = embeddingModelName;
        this.topK = topK;
    }

    @Transactional
    public AskResponse ask(String question) {
        long start = System.currentTimeMillis();
        String normalized = normalizeQuestion(question);
        List<RecallCandidate> candidates = recallService.recall(normalized, topK);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no evidence found for current question");
        }

        String answer = answerGenerator.generate(normalized, candidates);
        OffsetDateTime now = OffsetDateTime.now();
        Long qaId = newId();
        qaRepository.insertQaRecord(qaId, question, normalized, answer, (int) (System.currentTimeMillis() - start), now);
        qaRepository.insertRetrievalSnapshot(newId(), qaId, embeddingModelName, topK, candidates.size(), now);

        int rank = 1;
        int citeNo = 1;
        for (RecallCandidate candidate : candidates) {
            qaRepository.insertCandidate(newId(), qaId, candidate, rank++, now);
            qaRepository.insertEvidence(newId(), qaId, candidate, citeNo++, now);
        }

        List<EvidenceDto> evidences = candidates.stream().map(c -> new EvidenceDto(
                0,
                c.chunkId(),
                c.lawName(),
                c.articleNo(),
                c.content(),
                c.score()
        )).toList();
        return new AskResponse(qaId, normalized, answer, evidences);
    }

    @Transactional(readOnly = true)
    public QaDetailResponse getQa(Long qaId) {
        QaDetail detail = qaRepository.findQaDetail(qaId)
                .orElseThrow(() -> new IllegalArgumentException("qa record not found: " + qaId));
        List<EvidenceDto> evidenceDtos = detail.evidences().stream()
                .map(e -> new EvidenceDto(0, e.chunkId(), e.lawName(), e.articleNo(), e.content(), e.score()))
                .toList();
        return new QaDetailResponse(
                detail.qaId(),
                detail.question(),
                detail.normalizedQuestion(),
                detail.answer(),
                detail.answerStatus(),
                detail.createdAt(),
                evidenceDtos
        );
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim().replaceAll("\\s+", " ");
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
